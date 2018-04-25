package vgiotsas;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.*;
import java.util.*;

class CommunitiesParser implements Parser {

    private HashMap<String, String> routeServerASNs;
    private PeeringCartographer.ColocationMap coloMap;
    private HashMap<String, Integer> relationships = null;
    private HashMap<String, String> properties;

    CommunitiesParser(HashMap<String, String> properties) {
        this.properties = properties;
    }

    /**
     * Executes the different phases of the parsing process
     */
    @Override
    public void startParser(){
        // Construct the colocation map
        coloMap = PeeringCartographer.getColoMap(
                this.properties.get("pdb_netfac_url"),
                this.properties.get("ix_dataset"),
                this.properties.get("ix_asn_dataset")
        );

        // Read the external datasets

        this.relationships = readRelationships(this.properties.get("relationships_file"));

        this.routeServerASNs = PeeringCartographer.getRouteServerASNs(
                this.properties.get("pdb_rsasn_url"),
                this.properties.get("euroix_url"));

        String optionalArgs = this.constructOptionalArgs(
                this.properties.get("collectors"),
                this.properties.get("communities"),
                "",
                "");
        // Run an initial pass to find if there are any routes annotated with the target communities
        int start_ts = Integer.parseInt(this.properties.get("start"));
        int init_start = start_ts - 3600*24;
        List<String> requestedFacilities = new ArrayList<>();
        if (!this.properties.get("facilities").equals(CliParser.getDefaultFacilities())){
            requestedFacilities = Arrays.asList(this.properties.get("facilities").split(","));
        }
        int requestedOverlap = Integer.parseInt(this.properties.get("overlap"));
        Result result = getAnnotatedPaths(optionalArgs, init_start, start_ts, requestedFacilities, requestedOverlap);

        // If the initial pass discovered annotated routes, filter-out the unstable ones
        int routesNum = 0;
        for (String k: result.getRoutes().keySet()) {
            routesNum += result.getRoutes().get(k).size();
        }

        System.out.println("Annotated routes after initial pass: " + routesNum);
        if (routesNum > 0){
            int stability_seconds = Integer.parseInt(this.properties.get("stability_hours")) * 3600;
            int stability_end = start_ts + stability_seconds;
            optionalArgs = this.constructOptionalArgs(
                    String.join(", ", result.getCollectors()),
                    "",
                    String.join(", ", result.getPeers()),
                    String.join(", ", result.getPrefixes())
            );
            result.setRoutes(filterUnstablePaths(optionalArgs, result.getRoutes(), start_ts, stability_end));
            // check if there are any routes left after filtering-out the unstable ones
            routesNum = 0;
            for (String k: result.getRoutes().keySet()) {
                routesNum += result.getRoutes().get(k).size();
            }
            System.out.println("Annotated routes after filtering: " + routesNum);
            // if we still have annotated routes left, start monitoring their updates for the duration of the measurement period
            if (routesNum > 0){
                int monitoring_end = Integer.parseInt(this.properties.get("end"));
                optionalArgs = this.constructOptionalArgs(
                        String.join(", ", result.getCollectors()),
                        "",
                        String.join(", ", result.getPeers()),
                        String.join(", ", result.getPrefixes())
                );
                result.setRoutes(monitorAnnotatedPaths(optionalArgs, result.getRoutes(), stability_end, monitoring_end));
                // Parse the results to generate the timeline of routes annotated with target communities
                HashMap<String, Result.TimeLine> communitiesTimeline = result.getCommunitiesTimeline();
                // Write the results to a file
                writeResults(stability_end, monitoring_end, communitiesTimeline);
            }
        }
    }

    @Override
    public void writeResults(int startTs, int endTs, HashMap<String, Result.TimeLine> communitiesTimeline){
        PrintWriter writer = null;
        try {

            for (String community : communitiesTimeline.keySet()){
                writer = new PrintWriter(community+"-results.txt", "UTF-8");
                Map<Integer, Integer> tsMap = new TreeMap<>(communitiesTimeline.get(community).pathsByTs);
                //Set<Integer, Integer> timestamps = tsMap.entrySet();

                int nextMinute = startTs;
                Iterator<Integer> itr = tsMap.keySet().iterator();
                int nextTs = itr.next();
                //Map.Entry<Integer, Integer> tsEntry = tsMap.entrySet().iterator().next();
                //int nextTs = tsEntry.getKey();
                int nextValue = tsMap.get(nextTs);
                int totalPaths = 0;
                while (nextTs < nextMinute){
                    totalPaths += nextValue;
                    nextTs = itr.next();
                    nextValue = tsMap.get(nextTs);
                }

                while (nextMinute < endTs){
                    while (nextTs >= nextMinute && nextTs < nextMinute + 180){
                        totalPaths += nextValue;
                        if (itr.hasNext()){
                            nextTs = itr.next();
                            nextValue = tsMap.get(nextTs);
                        }
                        else{
                            break;
                        }
                    }
                    writer.println(nextMinute + "\t" + totalPaths);
                    nextMinute += 180;
                }

                writer.close();
            }

        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }

    }

    /**
     * Removes path prepending from an AS path, i.e. removes consecutive duplicates
     * @param path The AS path that is possibly prepended
     * @return an ArrayList with the hops in the non-prepended path
     */
    @Override
    public ArrayList<String> removePrepending(String[] path) {

        ArrayList<String> npPath = new ArrayList<>();
        // Always add first value
        npPath.add(path[0]);

        // Iterate the remaining values
        for(int i = 1; i < path.length; i++) {
            // Compare current value to previous
            if(!path[i-1].equals(path[i])) {
                npPath.add(path[i]);
            }
        }

        return npPath;
    }

    /**
     * Read CAIDA's AS Relationships dataset downloaded from
     * http://data.caida.org/datasets/as-relationships/serial-2/
     * The format of the relationships file is:
     * <provider-as>|<customer-as>|<relationship-value>|<source>
     * Relationship value of 1 indicates p2c, while value of 0 p2p relationship
     * @param fileIn the path to the bz2 relationship file
     * @return the mapping between AS links and relationship type
     */
    @Override
    public HashMap<String, Integer> readRelationships(String fileIn) {
        HashMap<String, Integer> relationships = new HashMap<>();
        try {
            FileInputStream fin = new FileInputStream(fileIn);
            BufferedInputStream bis = new BufferedInputStream(fin);
            CompressorInputStream input = new CompressorStreamFactory().createCompressorInputStream(bis);
            BufferedReader br2 = new BufferedReader(new InputStreamReader(input));

            String line;
            while ((line = br2.readLine()) != null) {
                if (!line.startsWith("#")){
                    String[] lf = line.split("\\|");
                    String link = lf[0] + " " + lf[1];
                    String invLink = lf[1] + " " + lf[0];
                    relationships.put(link, Integer.parseInt(lf[2]));
                    relationships.put(invLink, Integer.parseInt(lf[2]) * -1);
                }
            }
        }
        catch(IOException | CompressorException e){
            System.err.println("Error: Unable to read the AS relationships, the program will continue without using them. " +
                    "This may affect the accuracy and precision of the results.");
        }
        return relationships;
    }

    /**
     * Constructs the optional communities, collectors, peers and prefixes arguments of the BGPReader command based on the
     * comma-separated strings provided by the user command-line arguments.
     * @param collectors comma-separated list of BGP collectors
     * @param communities comma-separated list of BGP communities
     * @return the bgpreader arguments for the collectors and communities
     */
    @Override
    public String constructOptionalArgs(String collectors, String communities, String peers, String prefixes){
        StringBuilder args = new StringBuilder();

        if (!collectors.isEmpty() && !collectors.equals(CliParser.getDefaultCollectors())) {
            for (String c : collectors.split(",")) {
                args.append(" -c ").append(c);
            }
        }

        if (!communities.isEmpty()){
            for (String y: communities.split(",")) {
                args.append(" -y ").append(y);
            }
        }

        if (!peers.isEmpty()) {
            for (String y : peers.split(",")) {
                args.append(" -j ").append(y);
            }
        }

        if (!prefixes.isEmpty() && prefixes.length() < 1000) {
            for (String y : prefixes.split(",")) {
                args.append(" -k ").append(y);
            }
        }

        return args.toString();
    }

    /**
     * Finds which link in an AS path is annotated by a 32-bit community based on the top-16 bits of the community,
     * which by convention correspond to the ASN that defines the community value
     * @param path the AS path (space-separated list of ASNs)
     * @param communityTop16 the top 16 bits of the community value
     * @return String[] the annotated AS link (near-end and far-end hop)
     */
    @Override
    public String[] mapCommunityToLink(ArrayList<String> path, String communityTop16){
        String nearEnd = "";
        String farEnd = "";
        int hopIndex = -1;
        if (path.contains(communityTop16)){
            hopIndex = path.indexOf(communityTop16);
        }
        // If the community ASN is a Route Server ASN we need to consider two cases:
        // 1. If the Route Server ASN appears in the AS path, then the community tags the peering link between the ASN
        //    before the Route Server and the ASN after the Route Server (the IXP members that peer over the Route Server)
        // 2. If the Route Server ASN doesn't appear in the path, we need to infer which two ASNs are likely to peer ove
        //    the specific route server by checking the IXP membership data
        if (this.routeServerASNs.containsKey(communityTop16)){
            String ixpName = routeServerASNs.get(communityTop16);
            // case 1
            if (hopIndex > 0 && hopIndex < path.size() - 1){
                nearEnd = path.get(hopIndex - 1);
                farEnd = path.get(hopIndex + 1);
            }
            // case 2
            else if(this.coloMap.ixpMembers.containsKey(ixpName)) {
                int index = 0;
                int neIndex = -1;
                int feIndex = -1;
                for(String hop: path){
                    if(this.coloMap.ixpMembers.get(ixpName).contains(hop)){
                        if (neIndex == -1){
                            nearEnd = hop;
                            neIndex = index;
                        }else if(feIndex == -1){
                            if (index - neIndex == 1){
                                farEnd = hop;
                                feIndex = index;
                                String link = nearEnd + " " + farEnd;
                                if (!this.relationships.containsKey(link) || this.relationships.get(link) == 0){
                                    break; // we found the annotated link, stop searching
                                }
                            }
                            // if the near-end and the far-end are not consecutive, reset the near-end
                            else{
                                nearEnd = hop;
                                neIndex = index;
                            }
                        }
                        // if we have more than two consecutive IXP members in the path, check the relationships and
                        // select the near-end and far-end that have a p2p relationship
                        else if (index - feIndex == 1){
                            String link = farEnd + " " + hop;
                            if (!this.relationships.containsKey(link) || this.relationships.get(link) == 0){
                                nearEnd = farEnd;
                                farEnd = hop;
                                break; // we found the annotated link, stop searching
                            }
                        }
                    }
                    index ++;
                }
            }
        }
        // If the community ASN doesn't correspond to a Route Server, then the community tags the link between the
        // community ASN and the next hop. A special case is when the community ASN is the prefix origin. In this case
        // the community tags the location where the prefix is originated but not an AS link, so there's no far-end ASN
        else if (hopIndex >= 0){
            nearEnd = path.get(hopIndex);
            if (hopIndex < path.size() - 1) {
                farEnd = path.get(hopIndex + 1);
            }
        }

        return new String[]{nearEnd, farEnd};
    }

    /**
     * The initial pass on BGP data to collect all the paths in the start of the measurment period that are annotated
     * with the given communities. The initial paths. The initial pass begins one day before the provided measurement
     * start date onsiders only one RIB from each collector to bootstrap the parsing process with a set of annotated
     * paths.
     *
     * @param optionalArgs optional arguments of the bgpreader command to filter the parsed BGP data
     * @return Result object that stores the results of the BGP parsing process, including the annotated routes,
     * and the collectors, peers, and prefixes with annotated annotated routes.
     */
    @Override
    public Result getAnnotatedPaths(String optionalArgs, int init_start, int init_end, List<String> targetFacilities, int requestedOverlap){
        String command = properties.get("bgpreader_bin") +
                " -w " + init_start  + "," + init_end +
                " -t ribs" +
                " -P " + 3600*24 + "" +
                optionalArgs;
        System.out.println(command);

        HashSet<String> usefulCollectors = new HashSet<>();
        HashSet<String> usefulPeers = new HashSet<>();
        HashSet<String> usefulPrefixes = new HashSet<>();
        HashMap<String, HashMap<String, Route> > annotatedRoutes = new HashMap<>();

        HashSet<String> targetCommunities = new HashSet<>(Arrays.asList(this.properties.get("communities").split(",")));
        Process child;
        try {
            child = Runtime.getRuntime().exec(command);
            BufferedReader in = new BufferedReader(new InputStreamReader(child.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                // Format of bgpreader output:
                // <rec-type>|<elem-type>|<rec-ts-sec>.<rec-ts-usec>| \
                //  <project>|<collector>|<router>|<router-ip>|<peer-ASN>|<peer-IP>| \
                //  <prefix>|<next-hop-IP>|<AS-path>|<origin-AS>| \
                //  <communities>|<old-state>|<new-state>
                // https://bgpstream.caida.org/v2-beta
                String[] bgpFields = line.split("\\|");
                if (bgpFields[1].equals("R") && bgpFields.length > 9){
                    // For each community attached in the path that is part of the target communities provided by the
                    // user, find which AS link it annotates and inster the annotated BGP route in a HashMap that stores
                    // the annotated routes and the corresponding communities
                    String[] attachedCommunities = bgpFields[13].split(" ");
                    String timestamp = bgpFields[2].split("\\.")[0];
                    String peerIp = bgpFields[8];
                    String prefix = bgpFields[9];

                    for (String community: attachedCommunities) {
                        ArrayList<String> path = removePrepending(bgpFields[11].split(" "));
                        if (targetCommunities.contains(community)){
                            String top16bits = community.split(":")[0];
                            // if the top16 bits correspond to a Route Server ASN
                            String[] annotatedHops = this.mapCommunityToLink(path, top16bits);
                            if (!annotatedHops[0].isEmpty()){
                                boolean parseRoute = true;
                                // If certain facilities have been requested, check if the far-end hop is colocated in
                                // the target facilities
                                if (!targetFacilities.isEmpty()){
                                    parseRoute = false;
                                    if (!annotatedHops[1].isEmpty()){
                                        for (String facility : targetFacilities){
                                            if (this.coloMap.facMembers.containsKey(facility) && this.coloMap.facMembers.get(facility).contains(annotatedHops[1])){
                                                String facilityCity = this.coloMap.facilityCity.get(facility);
                                                int overlapSize = this.coloMap.autsysFacilities.get(annotatedHops[1]).get(facilityCity).size();
                                                if(overlapSize == requestedOverlap || requestedOverlap == -1){
                                                    parseRoute = true;
                                                    break;
                                                }
                                            }
                                        }
                                    }

                                }
                                if (parseRoute){
                                    Route annotatedRoute = new Route(
                                            community,
                                            attachedCommunities,
                                            annotatedHops[0],
                                            annotatedHops[1],
                                            prefix,
                                            peerIp);
                                    annotatedRoute.updateStatus(1, timestamp);
                                    if (!annotatedRoutes.containsKey(peerIp)){
                                        annotatedRoutes.put(peerIp, new HashMap<>());
                                    }
                                    annotatedRoutes.get(peerIp).put(prefix, annotatedRoute);
                                    usefulCollectors.add(bgpFields[4]);
                                    usefulPeers.add(bgpFields[7]);
                                    usefulPrefixes.add(prefix);
                                }
                            }
                        }
                    }
                }
            }

            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new Result(usefulCollectors, usefulPeers, usefulPrefixes, annotatedRoutes);
    }

    /**
     * This function receives the annotated paths collected in the initial pass and monitors the BGP updates for the
     * duration of the 'stability_period' property to detect changes in the communities values of these paths.
     * Paths that experience changes during this time window are discarded to keep only the stable paths that are
     * consistently annotated with the target community during the monitoring period.
     *
     * @param optionalArgs optional arguments of the bgpreader command to filter the parsed BGP data
     * @param initialRoutes the routes initially annotated with a target community
     * @return Object that stores the results of the BGP parsing process, including the annotated routes, the
     * collectors, peers, and prefixes with annotated annotated routes.
     */
    @Override
    public HashMap<String, HashMap<String, Route>> filterUnstablePaths(
            String optionalArgs, HashMap<String,
            HashMap<String, Route>> initialRoutes,
            int start_ts,
            int end_ts)
    {
        String command = properties.get("bgpreader_bin") +
                " -w " + start_ts + "," + end_ts +
                " -t ribs" +
                optionalArgs;
        System.out.println(command);

        Process child;
        try {
            child = Runtime.getRuntime().exec(command);
            BufferedReader in = new BufferedReader(new InputStreamReader(child.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                // Format of bgpreader output:
                // <rec-type>|<elem-type>|<rec-ts-sec>.<rec-ts-usec>| \
                //  <project>|<collector>|<router>|<router-ip>|<peer-ASN>|<peer-IP>| \
                //  <prefix>|<next-hop-IP>|<AS-path>|<origin-AS>| \
                //  <communities>|<old-state>|<new-state>
                // https://bgpstream.caida.org/v2-beta
                String[] bgpFields = line.split("\\|");
                if (bgpFields[1].equals("R") && bgpFields.length > 13){
                    String peerIp = bgpFields[8];
                    String prefix = bgpFields[9];
                    // If the route was initially annotated with a target community, check if it's still annotated
                    // with the same community
                    if (initialRoutes.containsKey(peerIp) && initialRoutes.get(peerIp).containsKey(prefix)){
                        List<String> attachedCommunities = Arrays.asList(bgpFields[13].split(" "));
                        Route  r = initialRoutes.get(peerIp).get(prefix);
                        if (!attachedCommunities.contains(r.getTargetCommunity())){
                            r = null;
                            initialRoutes.get(peerIp).remove(prefix);
                            if (initialRoutes.get(peerIp).size() == 0){
                                initialRoutes.remove(peerIp);
                            }
                        }
                        //r.updateStatus(attachedCommunities.contains(r.getTargetCommunity()) ? 1 : 0, timestamp);
                    }
                }
            }

            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return initialRoutes;
    }

    /**
     * The function that monitors the annotated paths for changes in the communities attribute or explicit withdrawals
     * Every time a change is detected the status of the route is updated, and the corresponding timestamp is recorded.
     * At the end of the measurement period for every annotated route we have a list of timestamps when the route
     * changed, and a list of timestamps when the route returned to the baseline target community.
     *
     * @param optionalArgs optional arguments of the bgpreader command to filter the parsed BGP data
     * @param annotatedRoutes the stable routes annotated with a target community
     * @return Object that stores the results of the BGP parsing process, including the annotated routes, and the
     * collectors, peers, and prefixes with annotated annotated routes.
     */
    @Override
    public HashMap<String, HashMap<String, Route>> monitorAnnotatedPaths(
            String optionalArgs, HashMap<String,
            HashMap<String, Route>> annotatedRoutes,
            int start_ts,
            int end_ts){

        String command = properties.get("bgpreader_bin") +
                " -w " + start_ts + "," + end_ts +
                " -t updates" +
                optionalArgs;
        System.out.println(command);

        Process child;
        try {
            child = Runtime.getRuntime().exec(command);
            BufferedReader in = new BufferedReader(new InputStreamReader(child.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                // Format of bgpreader output:
                // <rec-type>|<elem-type>|<rec-ts-sec>.<rec-ts-usec>| \
                //  <project>|<collector>|<router>|<router-ip>|<peer-ASN>|<peer-IP>| \
                //  <prefix>|<next-hop-IP>|<AS-path>|<origin-AS>| \
                //  <communities>|<old-state>|<new-state>
                // https://bgpstream.caida.org/v2-beta
                String[] bgpFields = line.split("\\|");
                if ( bgpFields.length > 9) {
                    String timestamp = bgpFields[2].split("\\.")[0];
                    String peerIp = bgpFields[8];
                    String prefix = bgpFields[9];

                    if (annotatedRoutes.containsKey(peerIp) && annotatedRoutes.get(peerIp).containsKey(prefix)) {
                        if (bgpFields[1].equals("A") && bgpFields.length > 13) {
                            // If the route is not annotated with the target community, set the status to withrawn,
                            // otherwise set the status to activated (if it has previously withdrawn).
                            List<String> attachedCommunities = Arrays.asList(bgpFields[13].split(" "));
                            Route r = annotatedRoutes.get(peerIp).get(prefix);
                            r.updateStatus(attachedCommunities.contains(r.getTargetCommunity()) ? 1 : 0, timestamp);
                        }
                        // If the route was withdrawn set the status to withdrawn
                        else if (bgpFields[1].equals("W")) {
                            Route r = annotatedRoutes.get(peerIp).get(prefix);
                            r.updateStatus(0, timestamp);
                        }
                    }
                }
            }

            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return annotatedRoutes;
    }

}
