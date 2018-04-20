package vgiotsas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Route {

    private String targetCommunity;
    private List<String> communities;
    private List<Integer> tsWithdrawn;
    private List<Integer> tsActivated;
    private String nearEnd;
    private String farEnd;
    private String prefix;
    private String peerIp;
    private int status;

    public Route(String tc, String[] communities, String ne, String fe, String pfx, String pip){
        this.targetCommunity = tc;
        this.communities = Arrays.asList(communities);
        this.nearEnd = ne;
        this.farEnd = fe;
        this.prefix = pfx;
        this.peerIp = pip;
        tsWithdrawn = new ArrayList<>();
        tsActivated = new ArrayList<>();
        status = 0; // 1 indicates activated route, 0 indicates withdrawn route
    }

    // GETTERS

    public String getTargetCommunity() {
        return targetCommunity;
    }

    public List<String> getCommunities() {
        return communities;
    }

    public List<Integer> getTsWithdrawn() {
        return tsWithdrawn;
    }

    public List<Integer> getTsReturned() {
        return tsActivated;
    }

    public String getNearEnd() {
        return nearEnd;
    }

    public String getFarEnd() {
        return farEnd;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getPeerIp() {
        return peerIp;
    }

    // SETTERS

    public void updateStatus(int status, String ts) {

        if (status != this.status){
            this.status = status;
            if (status == 0){
                tsWithdrawn.add(Integer.parseInt(ts));
            }
            else{
                tsActivated.add(Integer.parseInt(ts));
            }
        }
    }
}
