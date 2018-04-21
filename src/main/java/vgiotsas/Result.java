package vgiotsas;

import java.util.*;

class Result {

    class TimeLine{
        HashMap<Integer, Integer> pathsByTs;
    }

    private HashSet<String> collectors;
    private HashSet<String> peers;
    private HashSet<String> prefixes;
    private HashMap<String, HashMap<String, Route>> routes;
    private HashMap<String, TimeLine> communitiesTimeline;

    Result(HashSet<String> collectors,
           HashSet<String> peers,
           HashSet<String> prefixes,
           HashMap<String, HashMap<String, Route>> routes){
        this.collectors = collectors;
        this.peers = peers;
        this.prefixes = prefixes;
        this.routes = routes;
    }


    private void updateTimeline(String community, List<Integer> timestamps, int value){
        for (int ts : timestamps){
            if (!this.communitiesTimeline.get(community).pathsByTs.containsKey(ts)){
                this.communitiesTimeline.get(community).pathsByTs.put(ts, value);
            }
            else{
                if (this.communitiesTimeline.get(community).pathsByTs.containsKey(ts)){
                    int previous_paths = this.communitiesTimeline.get(community).pathsByTs.get(ts);
                    this.communitiesTimeline.get(community).pathsByTs.put(ts, previous_paths + value);
                }
            }
        }
    }

    HashMap<String, TimeLine> getCommunitiesTimeline(){
        communitiesTimeline = new HashMap<>();
        for (String peerIp : this.routes.keySet()){
            for (String prefix : this.routes.get(peerIp).keySet()){
                String community = this.routes.get(peerIp).get(prefix).getTargetCommunity();
                List<Integer> activated = this.routes.get(peerIp).get(prefix).getTsActivated();
                List<Integer> withdrawn = this.routes.get(peerIp).get(prefix).getTsWithdrawn();
                if (!communitiesTimeline.containsKey(community)){
                    communitiesTimeline.put(community, new TimeLine());
                }
                updateTimeline(community, activated, 1);
                updateTimeline(community, withdrawn, -1);
            }
        }
        return this.communitiesTimeline;
    }

    HashSet<String> getCollectors() {
        return collectors;
    }

    HashSet<String> getPeers() {
        return peers;
    }

    HashSet<String> getPrefixes() {
        return prefixes;
    }

    HashMap<String, HashMap<String, Route>> getRoutes() {
        return routes;
    }

    void setRoutes(HashMap<String, HashMap<String, Route>> routes) {
        this.routes = routes;
    }
}
