package vgiotsas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public interface Parser {
    void startParser();

    void writeResults(int startTs, int endTs, HashMap<String, Result.TimeLine> communitiesTimeline);

    ArrayList<String> removePrepending(String[] path);

    HashMap<String, Integer> readRelationships(String fileIn);

    String constructOptionalArgs(String collectors, String communities, String peers, String prefixes);

    String[] mapCommunityToLink(ArrayList<String> path, String communityTop16);

    Result getAnnotatedPaths(String optionalArgs, int init_start, int init_end, List<String> targetFacilities, int requestedOverlap);

    void filterUnstablePaths(
            String optionalArgs,
            //HashMap<String, HashMap<String, Route>> initialRoutes,
            int start_ts,
            int end_ts);

    void monitorAnnotatedPaths(
            String optionalArgs,
            //HashMap<String, HashMap<String, Route>> annotatedRoutes,
            int start_ts,
            int end_ts);
}
