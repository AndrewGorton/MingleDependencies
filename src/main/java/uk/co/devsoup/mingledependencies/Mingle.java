package uk.co.devsoup.mingledependencies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Mingle {
    private static final Logger LOGGER = LoggerFactory.getLogger(Mingle.class);

    private final ExecutorService threadpool = Executors.newFixedThreadPool(20);

    public Mingle() {
    }


    public boolean generateDependencies(final Set<Integer> storiesToProcess,
                                     final Map<Integer, StoryDetails> storyDetails,
                                     final Map<Integer, List<Integer>> dependencies) {
        boolean success = false;

        if (storiesToProcess.size() < 10) {
            threadpool.submit(new GenerateDependenciesWorker(storiesToProcess, storyDetails, dependencies));
        } else {
            Iterator<Integer> it = storiesToProcess.iterator();
            Set<Integer> splitSet = new HashSet<Integer>();
            int counter = 0;
            while (it.hasNext()) {
                Integer i = it.next();
                splitSet.add(i);

                if (++counter % 10 == 0) {
                    threadpool.execute(new GenerateDependenciesWorker(splitSet, storyDetails, dependencies));
                    splitSet = new HashSet<Integer>();
                }
            }

            if (splitSet.size() > 0) {
                threadpool.submit(new GenerateDependenciesWorker(splitSet, storyDetails, dependencies));
            }
        }

        threadpool.shutdown();
        LOGGER.info("Waiting for child workers to finish");
        while (!threadpool.isTerminated()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Don't care
            }
        }

        if(!GenerateDependenciesWorker.isErrored()) {
            success = true;
        }

        return success;
    }
}
