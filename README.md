# MingleDependencies

## Overview
A tool for generating a dependency graph between stories for better visualisation of the order stories should be played.
This was tested using a self-hosted version of Mingle 13_2_1, rather than a cloud-hosted version (but it may work there too).

It produces a [Dot](http://en.wikipedia.org/wiki/DOT_%28graph_description_language%29) file, which [GraphViz](http://www.graphviz.org) can then turn into a GIF.

To mark up your stories for this, please put

```
Depends_Upon(#1234)
```

or

```
Depends_Upon(#1234,#2345,#3456)
```

into the story description. The tool extracts these to generate a backward dependency graph.

## Requires

* JDK 1.7
* Maven 3.1.1
* GraphViz 2.38.0

## Building
Maven builds a single, executable fatjar with all dependencies in it:-

```
mvn package
```

## Running

You'll want a copy of GraphViz installed. For a Mac, try `brew install graphviz`.

To run:-
```
export MINGLE_USERNAME=<your_username_here>
export MINGLE_PASSWORD=<your_password_here>
export MINGLE_SERVER_SCHEME=http
export MINGLE_SERVER=<your_server_here>
export MINGLE_MQL_PATH=/api/v2/projects/<project_name>/cards/execute_mql.xml

# To search the entire corpus of stories and extract dependencies
java -jar java -jar target/MingleDependencies-1.0.1-SNAPSHOT-jar-with-dependencies.jar storyRange 1,<max_story_number>
# Or to selectively process stories
#java -jar target/MingleDependencies-1.0.1-SNAPSHOT-jar-with-dependencies.jar stories 4345,4349,4717,4897,5027,5092,5120,5071,5109,5119

# This produces a DOT file for GraphViz which can be changed into a GIF with
dot -Tgif -O dependency-graph.dot

# To open on a Mac
open dependency-graph.dot.gif
```

## Configuration of output

You may want to tailor the statuses listed at the top of the DotFileGenerator which defines the node colours based upon the story status.


## To Do
1. Figure out the max story automagically for storyRange parameter
2. Split the dependency graph into a single graph for each dependency set
3. Provide forward and backward dependencies.
4. Automatically query stories which are listed as dependencies, but are not listed in the "stories" parameter
5. Handle errors from child worker threads which query Mingle better (eg to avoid producing a partial Dot file.

