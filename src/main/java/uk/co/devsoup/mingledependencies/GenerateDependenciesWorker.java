package uk.co.devsoup.mingledependencies;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenerateDependenciesWorker implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateDependenciesWorker.class);

    private final Set<Integer> storiesToProcess;

    // WARNING: These are shared amongst threadpool threads, so use appropriate locks
    private Map<Integer, StoryDetails> storyDetails;
    private Map<Integer, List<Integer>> dependencies;
    // Used to protect the above
    private static final Object LOCK_OBJECT = new Object();
    private static boolean errored = false;

    private static final String DEFAULT_STATUS = "unknown";

    private static final Pattern DEPEND_PATTERN = Pattern.compile("[Dd][Ee][Pp][Ee][Nn][Dd][Ss]_[Uu][Pp][Oo][Nn]\\((.*?)\\)");
    private static final Pattern DEPEND_SPLITTER = Pattern.compile("#([\\d]+)");

    private String username;
    private String password;
    private String mingle_server_scheme;
    private String mingle_server;
    private String mingle_mql_path;

    public GenerateDependenciesWorker(final Set<Integer> storiesToProcess,
                                      final Map<Integer, StoryDetails> storyDetails,
                                      final Map<Integer, List<Integer>> dependencies) {
        this.storiesToProcess = storiesToProcess;
        this.storyDetails = storyDetails;
        this.dependencies = dependencies;

        username = System.getenv("MINGLE_USERNAME");
        password = System.getenv("MINGLE_PASSWORD");
        mingle_server_scheme = System.getenv("MINGLE_SERVER_SCHEME");
        mingle_server = System.getenv("MINGLE_SERVER");
        mingle_mql_path = System.getenv("MINGLE_MQL_PATH");

    }

    public static boolean isErrored() {
        return errored;
    }

    @Override
    public void run() {
        try {
            // If some other thread has already errored, give up
            if(errored) {
                return;
            }

            List<Integer> myList = new ArrayList<Integer>();
            for(Integer tempStory : storiesToProcess) {
                myList.add(tempStory);
            }
            Collections.sort(myList);
            LOGGER.debug(String.format("Processing stories from '%d' to '%d'", myList.get(0), myList.get(myList.size()-1)));

            String storyList = StringUtils.join(storiesToProcess, ",");
            URI uri = new URIBuilder()
                    .setScheme(mingle_server_scheme)
                    .setHost(mingle_server)
                    .setPath(mingle_mql_path)
                    .setParameter("mql", "SELECT number, name, type, description, status " +
                                    "WHERE number IN (" + storyList + ")"
                    )
                    .setUserInfo(username, password)
                    .build();

            HttpGet request = new HttpGet(uri);
            CloseableHttpClient client = HttpClients.createDefault();
            LOGGER.debug("Querying server for story information");
            CloseableHttpResponse response = client.execute(request);

            // If another thread has errored, don't bother processing the response
            if(errored) {
                return;
            }

            HttpEntity entity = response.getEntity();
            String responseBody = EntityUtils.toString(entity);
            LOGGER.debug("Processing response");
            LOGGER.trace("HTTP Response from Mingle: " + responseBody);

            // Parse
            StringReader sr = new StringReader(responseBody);
            InputSource is = new InputSource(sr);
            XPathFactory xpf = XPathFactory.newInstance();
            XPath xp = xpf.newXPath();
            NodeList nl = (NodeList) xp.evaluate("/results/result", is, XPathConstants.NODESET);
            Pattern r2 = Pattern.compile("#\\d+");

            for (int i = 0; i < nl.getLength(); i++) {
                NodeList resultNode = nl.item(i).getChildNodes();
                Map<String, String> storyMeta = new HashMap<String, String>();
                for (int j = 0; j < resultNode.getLength(); j++) {

                    String key = resultNode.item(j).getNodeName();
                    if (key.compareTo("#text") != 0) {
                        String value = resultNode.item(j).getTextContent();
                        //LOGGER.debug(String.format("%s=%s", key, value));
                        storyMeta.put(key, value);
                    }
                }

                if (StringUtils.isBlank(storyMeta.get("status"))) {
                    storyMeta.put("status", "unknown");
                }

                processStory(storyMeta, storyDetails, dependencies);
            }
        } catch (Exception e) {
            errored = true;
            LOGGER.error(e.getMessage());
            LOGGER.debug(ExceptionUtils.getStackTrace(e));
        }
    }

    private void processStory(final Map<String, String> storyMetaInf,
                              final Map<Integer, StoryDetails> storyDetails,
                              final Map<Integer, List<Integer>> dependencies) {
        // Process the story
        if (StringUtils.isNotBlank(storyMetaInf.get("type")) &&
                storyMetaInf.get("type").compareToIgnoreCase("story") == 0) {
            int storyNumber = Integer.parseInt(storyMetaInf.get("number"));
            String storyName = storyMetaInf.get("name");
            String status = storyMetaInf.get("status");
            LOGGER.trace(String.format("%d: %s", storyNumber, storyName));
            StoryDetails tempStory = new StoryDetails();
            tempStory.setNumber(storyNumber);
            tempStory.setName(storyName);
            tempStory.setStatus(status);

            synchronized (LOCK_OBJECT) {
                storyDetails.put(storyNumber, tempStory);
            }

            String description = storyMetaInf.get("description");
            Matcher m = DEPEND_PATTERN.matcher(description);
            if (m.find()) {
                LOGGER.debug(String.format("Dependency macro detected in story '%d'", storyNumber));
                if (m.groupCount() > 0) {
                    LOGGER.trace("Depend_upon contents: " + m.group(1));
                    processDependencies(m.group(1), storyNumber, dependencies);
                }
            }
        }
    }

    private void processDependencies(final String dependencyText, final int storyNumber, final Map<Integer, List<Integer>> dependencies) {
        if (StringUtils.isNotBlank(dependencyText)) {
            LOGGER.trace(String.format("Attempting to parse: '%s'", dependencyText));
            String sanitisedText = dependencyText.replaceAll("<[^>]+?>", "");
            LOGGER.trace(String.format("Sanitised text: '%s'", sanitisedText));
            Matcher m = DEPEND_SPLITTER.matcher(sanitisedText);
            while (m.find()) {
                if (m.groupCount() > 0) {
                    int tempDependency = Integer.parseInt(m.group(1));
                    if (tempDependency != storyNumber) { // Avoid direct links back to self
                        LOGGER.debug(String.format("Dependency: %d", tempDependency));
                        synchronized (LOCK_OBJECT) {
                            if (dependencies.containsKey(storyNumber)) {
                                dependencies.get(storyNumber).add(tempDependency);
                            } else {
                                List tempList = new ArrayList<Integer>();
                                tempList.add(tempDependency);
                                dependencies.put(storyNumber, tempList);
                            }
                        }
                    }
                }
            }
        }
    }
}
