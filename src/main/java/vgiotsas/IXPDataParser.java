package vgiotsas;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.net.HttpURLConnection;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

class IXPDataParser {

    private static final String USER_AGENT = "Mozilla/5.0";

    /**
     * Extract and facility AS members from PeeringDB
     * @param pdbFacUrl The URL of the PeeringDB API endpoint
     * @return HashMap<String, List<String>> The mapping between facility names and the ASNs present at them
     */
    static HashMap<String, List<String>> getFacilityMembers(String pdbFacUrl){
        ArrayList<String> pdbResponse = IXPDataParser.sendGet(pdbFacUrl);
        JSONObject pdbData = IXPDataParser.parseJson(String.join("", pdbResponse));
        HashMap<String, List<String>> facMembers = new HashMap<>();
        JSONArray nets_array = (JSONArray) pdbData.get("data");
        if (nets_array != null){
            for (Object obj : nets_array) {
                if (obj instanceof JSONObject) {
                    JSONObject jsonObj = (JSONObject) obj;

                    String asn = Integer.toString((int) (long) jsonObj.get("local_asn"));
                    String name = jsonObj.get("name").toString();
                    if (!facMembers.containsKey(name)) {
                        facMembers.put(name, new ArrayList<>());
                    }
                    facMembers.get(name).add(asn);
                }
            }
        }

        return facMembers;
    }

    /**
     * Parses the CAIDA IX dataset to map IXPs to their AS members
     * @param ix_file File that contains information about individual IXPs
     * @param ixasn_file File that maps ASes to the IXPs where they are present
     * @return the mapping between each IXP and the list of ASNs connected to the IXP
     */
    static HashMap<String, List<String>> getIXPMembers(String ix_file, String ixasn_file) {
        HashMap<String, List<String>> ixpMembers = new HashMap<>();
        HashMap<String, String> ixpIdNames = new HashMap<>();

        try {
            Files.lines(new File(ix_file).toPath())
                    .map(String::trim)
                    .filter(s -> !s.startsWith("#"))
                    .map(s-> parseJson(s.trim()))
                    .forEach(item -> {
                        ixpIdNames.put(item.get("ix_id").toString(), item.get("name").toString());
                        ixpMembers.put(item.get("name").toString(), new ArrayList<>());
                    });

            Files.lines(new File(ixasn_file).toPath())
                    .map(String::trim)
                    .filter(s -> !s.startsWith("#"))
                    .map(s-> parseJson(s.trim()))
                    .forEach(item -> {
                        String ixpName = ixpIdNames.get(item.get("ix_id").toString());
                        ixpMembers.get(ixpName).add(item.get("asn").toString());
                    });

        } catch (IOException e) {
            e.printStackTrace();
        }
        return ixpMembers;
    }

    /**
     * Queries the PeeringDB and Euro-IX data to collect the ASNs used by IXP route servers
     * @param pdbUrl The URL to the PeeringDB endpoint to query ASNs of type Route Server
     * @param euroixUrl The URL to the Euro-IX endpoint that provides data about IXP properties
     * @return HashMap<String, String> The mapping between Route Server ASNs and the corresponding IXP names
     */
    static HashMap<String, String> getRouteServerASNs(String pdbUrl, String euroixUrl) {
        ArrayList<String> pdbResponse = IXPDataParser.sendGet(pdbUrl);
        ArrayList<String> euroixResponse = IXPDataParser.sendGet(euroixUrl);
        JSONObject pdbData = IXPDataParser.parseJson(String.join("", pdbResponse));
        HashMap<String, String> rsASNs = IXPDataParser.parsePdbRsAutsysData(pdbData);
        if (euroixResponse != null){
            rsASNs.putAll(IXPDataParser.parseEuroIXData(euroixResponse));
        }
        return rsASNs;
    }

    /**
     * Issues an HTTP GET request and returns the body of the reply as list of lines
     * @param url The URL to which the GET request is sent
     * @return ArrayList<String> The list of lines in the response body
     */
    private static ArrayList<String> sendGet(String url) {

        URL obj = null;
        ArrayList<String> response = null;
        try {
            obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", USER_AGENT);
            //int responseCode = con.getResponseCode();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            // StringBuffer response = new StringBuffer();
            response = new ArrayList<>();
            while ((inputLine = in.readLine()) != null) {
                response.add(inputLine);
            }
            in.close();
        } catch (IOException e) {
            System.err.println("Couldn't reach URL " + url);
        }

        return response;
    }

    /**
     * Convert JSON string to JSONObject
     * @param jsonString The string that represents a JSON object
     * @return JSONObject The decoded JSON object
     */
    private static JSONObject parseJson(String jsonString) {
        JSONObject jsonObject = null;
        JSONParser parser = new JSONParser();
        try {
            Object obj = parser.parse(jsonString);
            jsonObject = (JSONObject) obj;
        } catch (ParseException pe) {

            System.out.println("position: " + pe.getPosition());
            System.out.println(pe);
        }

        return jsonObject;
    }


    /**
     * Parse the PeeringDB JSON response to extract and return the Route Server ASNs
     * @param pdbResponse The JSON response from the PeeringDB API
     * @return HashMap<String, String> The mapping between Route Server ASNs and the corresponding IXP names
     */
    private static HashMap<String, String> parsePdbRsAutsysData(JSONObject pdbResponse){
        HashMap<String, String> asns = new HashMap<>();
        JSONArray nets_array = (JSONArray) pdbResponse.get("data");
        if (nets_array != null){
            Iterator iterator = nets_array.iterator();
            while (iterator.hasNext()) {
                Object obj = iterator.next();
                if(obj instanceof JSONObject) {
                    JSONObject jsonObj = (JSONObject) obj;

                    String asn = Integer.toString((int) (long) jsonObj.get("asn"));
                    String name = jsonObj.get("aka").toString();
                    asns.put(asn, name);
                }
            }
        }

        return asns;
    }

    /**
     * Parse the Euro-IX CSV file with the IXP data to get the Route Server & IXP ASNs
     * @param euroixResponse The lines of the Euro-IX CSV file
     * @return HashMap<String, String> The mapping between Route Server ASNs and the corresponding IXP names
     */
    private static HashMap<String, String> parseEuroIXData(ArrayList<String> euroixResponse) {
        HashMap<String, String> asns = new HashMap<>();
        for (String line : euroixResponse) {
            String[] lf = line.split(",");
            for (int i=2; i<4; i++) {
                if (lf[i].replaceAll("\\s+","").length() > 0){
                    try{
                        String asn = lf[i].replace("AS", "");
                        if (Integer.parseInt(asn) > 0) {
                            asns.put(asn, lf[0]);
                            System.out.println(lf[i]);
                        }
                    }
                    catch (NumberFormatException n) {
                        continue;
                    }
                }
            }
        }

        return asns;
    }

}
