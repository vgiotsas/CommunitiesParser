package vgiotsas;

import java.util.HashSet;
import java.util.HashMap;

public class ParsingResult {

    private HashSet<String> collectors;
    private HashSet<String> peers;
    private HashSet<String> prefixes;
    private HashMap<String, HashMap<String, Route>> routes;

    ParsingResult(HashSet<String> collectors,
                         HashSet<String> peers,
                         HashSet<String> prefixes,
                         HashMap<String, HashMap<String, Route>> routes){
        this.collectors = collectors;
        this.peers = peers;
        this.prefixes = prefixes;
        this.routes = routes;
    }

    public HashSet<String> getCollectors() {
        return collectors;
    }

    public HashSet<String> getPeers() {
        return peers;
    }

    public HashSet<String> getPrefixes() {
        return prefixes;
    }

    public HashMap<String, HashMap<String, Route>> getRoutes() {
        return routes;
    }
}
