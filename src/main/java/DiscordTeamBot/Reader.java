package DiscordTeamBot;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Reader {
	public String readFileAsString(String path) throws Exception  {
        String fileAsString = new String(Files.readAllBytes(Paths.get(path)));
        return fileAsString;
    }
	public boolean fileExists(String path) {
		return new File(path).exists();
	}
	public void deleteFile(String path) {
		new File(path).delete();
	}
}
