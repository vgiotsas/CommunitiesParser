package vgiotsas;

import java.io.IOException;
import java.util.HashMap;

public class Main {

    public static void main(String[] args) throws IOException {
        System.out.println("Start");
        // Read the arguments from the command-line and the configuration file
        CliParser cliParser = new CliParser();
        ConfigReader cfgReader = new ConfigReader();
        HashMap<String, String> arguments = cliParser.cliParser(args);
        HashMap<String, String> properties = cfgReader.getPropValues();
        arguments.putAll(properties);

        CommunitiesParser communitiesParser = new CommunitiesParser(arguments);
        communitiesParser.startParser();
    }
}
