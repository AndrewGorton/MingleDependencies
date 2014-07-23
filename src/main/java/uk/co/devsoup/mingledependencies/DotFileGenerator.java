package uk.co.devsoup.mingledependencies;

import org.apache.commons.lang3.StringUtils;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DotFileGenerator {
    private static final String[] INPROGRESS_STATUSES = new String[]{"ready for code review", "in development"};
    private static final String[] ABANDONED_STATUSES = new String[]{"discarded", "out of scope"};

    public void generateOutput(final Map<Integer, StoryDetails> storyDetails, final Map<Integer, List<Integer>> dependencies) {
        // Now create a DOT file for processing
        try {
            Set<Integer> nodesWhichNeedWriting = new HashSet<Integer>();
            PrintWriter writer = new PrintWriter("dependency-graph.dot", "UTF-8");
            writer.println("digraph dependencies {");
            for (Map.Entry<Integer, List<Integer>> kvp : dependencies.entrySet()) {
                nodesWhichNeedWriting.add(kvp.getKey());
                for (Integer singleInt : kvp.getValue()) {
                    writer.println(String.format("   %d -> %d;", singleInt, kvp.getKey()));
                    nodesWhichNeedWriting.add(singleInt);
                }
            }

            for (Integer singleStoryId : nodesWhichNeedWriting) {
                if (storyDetails.containsKey(singleStoryId)) {
                    StoryDetails storyDetail = storyDetails.get(singleStoryId);
                    // Escape quotes in story titles
                    String storyName = storyDetail.getName().replace("\"", "\\\"");
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format(" %d [label = \"%d: %s\", shape=box", storyDetail.getNumber(),
                            storyDetail.getNumber(),
                            storyName));
                    String defaultColour = "black";
                    if (StringUtils.isNotBlank(storyDetail.getStatus())) {
                        if (storyDetail.getStatus().compareToIgnoreCase("accepted") == 0) {
                            defaultColour = "forestgreen";
                        } else if (isInProgress(storyDetail.getStatus())) {
                            defaultColour = "blue";
                        }
                    } else if (isAbandoned(storyDetail.getStatus())) {
                        defaultColour = "violetred";
                    }
                    sb.append(String.format(", color=\"%s\"", defaultColour));
                    sb.append(String.format(", fontcolor=\"%s\"", defaultColour));

                    sb.append("]");
                    writer.println(sb.toString());
                }
            }

            writer.println("   labelloc=\"t\"");
            writer.println("   label=\"MingleDependencies @ " + new java.util.Date().toString() + "\"");


            writer.println("}");
            writer.close();

            System.out.println("Now execute: dot -Tgif -O dependency-graph.dot && open dependency-graph.dot.gif");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isInProgress(final String status) {
        boolean isInProgress = false;

        for (String singleStatus : INPROGRESS_STATUSES) {
            if (status.compareToIgnoreCase(singleStatus) == 0) {
                isInProgress = true;
                break;
            }
        }

        return isInProgress;

    }

    private boolean isAbandoned(final String status) {
        boolean isAbandoned = false;

        for (String singleStatus : ABANDONED_STATUSES) {
            if (status.compareToIgnoreCase(singleStatus) == 0) {
                isAbandoned = true;
                break;
            }
        }

        return isAbandoned;

    }
}
