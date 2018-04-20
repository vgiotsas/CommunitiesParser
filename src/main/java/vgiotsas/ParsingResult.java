package vgiotsas;

import java.util.HashSet;
import java.util.HashMap;

class ParsingResult {

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
