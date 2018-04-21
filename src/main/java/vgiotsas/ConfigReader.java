package vgiotsas;

import java.io.File;
import java.util.HashMap;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.io.FileNotFoundException;

class ConfigReader {
    private InputStream inputStream;

    HashMap<String, String> getPropValues() throws IOException {
        HashMap<String, String> properties = new HashMap<>();
        try {
            Properties prop = new Properties();
            String propFileName = "config.properties";
            inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);

            if (inputStream != null) {
                prop.load(inputStream);
            } else {
                throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
            }

            // get the property value and put it in the properties map
            prop.forEach((key, value) -> properties.put(key.toString(), value.toString()));
        } catch (Exception e) {
            System.out.println("Exception: " + e);
        } finally {
            if(inputStream != null){
                inputStream.close();
            }
        }
        return properties;
    }
}