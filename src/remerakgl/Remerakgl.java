package src.remerakgl;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class Remerakgl {

  private static final String DB_URL = "jdbc:mysql://127.0.0.1:3306/";
  private static final String DB_USER = "";
  private static final String DB_PASSWORD = "";
  private static final List<String> PHP_SCRIPTS = Arrays.asList(
    "client_registry_uploader.php",
    "uploaderEncounterVital.php",
    "uploaderVitalObservation.php",
    "uploaderEncounterConsult.php",
    "uploaderDiagObservation.php",
    "uploaderPlaintObservation.php",
    "uploaderEncounterLabo.php",
    "uploaderExamObservation.php",
    "uploaderEncounterMed.php",
    "uploaderMedObservation.php"
  );

  public static void main(String[] args) {
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    scheduler.scheduleAtFixedRate(
      () -> {
        System.out.println("Running scheduled task...");
        if (checkInternetConnection()) {
          processEncounters();
        } else {
          System.out.println("Skipping script execution due to no internet.");
        }
      },
      0,
      1,
      TimeUnit.MINUTES
    );
  }

  private static boolean checkInternetConnection() {
    try {
      return InetAddress.getByName("google.com").isReachable(2000);
    } catch (IOException e) {
      return false;
    }
  }

  private static void processEncounters() {
    try (
      Connection conn = DriverManager.getConnection(
        DB_URL,
        DB_USER,
        DB_PASSWORD
      )
    ) {
      String query =
        "SELECT e.client_id, e.encount_id FROM encounters e WHERE e.rhie_status = 2 ORDER BY e.time ASC";
      try (
        PreparedStatement stmt = conn.prepareStatement(query);
        ResultSet rs = stmt.executeQuery()
      ) {
        Map<Integer, List<Integer>> encounters = new LinkedHashMap<>();
        while (rs.next()) {
          encounters
            .computeIfAbsent(rs.getInt("client_id"), k -> new ArrayList<>())
            .add(rs.getInt("encount_id"));
        }
        for (Map.Entry<Integer, List<Integer>> entry : encounters.entrySet()) {
          executeScriptsForClient(conn, entry.getKey(), entry.getValue());
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  private static void executeScriptsForClient(
    Connection conn,
    int clientID,
    List<Integer> encounterIDs
  ) {
    try {
      PreparedStatement stmt = conn.prepareStatement(
        "UPDATE encounters SET rhie_status = 3 WHERE client_id = ? AND rhie_status = 2"
      );
      stmt.setInt(1, clientID);
      stmt.executeUpdate();

      for (int encounterID : encounterIDs) {
        for (String script : PHP_SCRIPTS) {
          executePhpScript(script, clientID, encounterID);
        }
        markEncounterAsCompleted(conn, encounterID);
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  private static void executePhpScript(
    String script,
    int clientID,
    int encounterID
  ) {
    try {
      String phpCommand = "php " + script + " " + clientID + " " + encounterID;
      Process process = Runtime.getRuntime().exec(phpCommand);
      process.waitFor();
      System.out.println(
        "Executed: " +
        script +
        " for Client ID: " +
        clientID +
        ", Encounter ID: " +
        encounterID
      );
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  private static void markEncounterAsCompleted(
    Connection conn,
    int encounterID
  ) {
    try {
      PreparedStatement stmt = conn.prepareStatement(
        "UPDATE encounters SET rhie_status = 1 WHERE encount_id = ?"
      );
      stmt.setInt(1, encounterID);
      stmt.executeUpdate();
      System.out.println(
        "Encounter ID " + encounterID + " marked as completed."
      );
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }
}
