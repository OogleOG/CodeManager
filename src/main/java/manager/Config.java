package manager;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    private static final String CONFIG_PATH = "config.properties";
    private static final Properties PROPS = new Properties();

    static {
        try (FileInputStream fis = new FileInputStream(CONFIG_PATH)) {
            PROPS.load(fis);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getGithubToken() {
        return PROPS.getProperty("github.token");
    }
}

