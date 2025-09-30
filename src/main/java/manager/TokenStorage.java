package manager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

public class TokenStorage {

    // Point to project root instead of user home
    private static final Path PATH = Paths.get("config.properties");

    public static void saveToken(String token) {
        try {
            Files.writeString(PATH, token);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String loadToken() {
        try {
            if (Files.exists(PATH)) {
                return Files.readString(PATH).trim();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
