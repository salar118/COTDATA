import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class ReadCOT {

  final static String COMMA_SPACE = ";";
  final static LocalDateTime startWeek = LocalDateTime.of(2023, 04, 04, 0, 0);
  final static DateTimeFormatter CUSTOM_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

  final static String siteUrl = "https://www.tradingster.com/cot/legacy-futures/";
  final static String fileName = "COTReports.txt";
  final static int numOfWeeksToLookBack = 100;
  private static final int OPEN_INTEREST = 0;
  private static final int NON_COMMERCIAL_LONG = 1;
  private static final int NON_COMMERCIAL_SHORT = 2;
  private static final int NON_COMMERCIAL_SPREADS = 3;
  private static final int COMMERCIAL_LONG = 4;
  private static final int COMMERCIAL_SHORT = 5;
  private static final int NON_REPORTABLE_LONG = 8;
  private static final int NON_REPORTABLE_SHORT = 9;

  private static String retrieveData(Elements content, int elementIndex) {
    return content.get(elementIndex).text().replace(",", "");
  }

  private static String generateWeeKCotReportLine(Elements content, LocalDateTime cotWeek, IndexEnum index) {
    String cotWeekString = generateWeekString(cotWeek);
    int comIntention = calculateParticipanteIntention(retrieveData(content, COMMERCIAL_LONG) , retrieveData(content, COMMERCIAL_SHORT));
    int nonComIntention = calculateParticipanteIntention(retrieveData(content, NON_COMMERCIAL_LONG) , retrieveData(content, NON_COMMERCIAL_SHORT));
    int nonReportableIntention = calculateParticipanteIntention(retrieveData(content, NON_REPORTABLE_LONG) , retrieveData(content, NON_REPORTABLE_SHORT));
    int totalLong = calculateTotalLong(comIntention, nonComIntention);
    int totalShort = calculateTotalShort(comIntention, nonComIntention);

    return
        cotWeekString + COMMA_SPACE +
        nonComIntention + COMMA_SPACE +
        comIntention + COMMA_SPACE +
        nonReportableIntention + COMMA_SPACE +
        totalLong + COMMA_SPACE +
        totalShort + COMMA_SPACE +
        COMMA_SPACE +
        retrieveData(content, NON_COMMERCIAL_LONG) + COMMA_SPACE +
        "-" + retrieveData(content, NON_COMMERCIAL_SHORT) + COMMA_SPACE +
        retrieveData(content, COMMERCIAL_LONG) + COMMA_SPACE +
        "-" + retrieveData(content, COMMERCIAL_SHORT) + COMMA_SPACE +
        retrieveData(content, NON_REPORTABLE_LONG) + COMMA_SPACE +
        "-" + retrieveData(content, NON_REPORTABLE_SHORT) +
        COMMA_SPACE + COMMA_SPACE +
        index.indexName;
  }

  private static int calculateParticipanteIntention(String noOfLong, String noOfShort){
    int longP = Integer.parseInt(noOfLong);
    int shortP = Integer.parseInt(noOfShort);
    return longP - shortP;
  }

  private static int calculateTotalLong(int noOfCom, int noOfNonCom){
    //=IF(L4>0;IF(K4<0;L4-K4;L4+K4);0)
    if (noOfCom > 0){
      return noOfNonCom<0 ? noOfCom - noOfNonCom : noOfCom + noOfNonCom;
    }
    return 0;
  }

  private static List<String> readCotLinesFromFile(String fileName) throws IOException {
    List<String> allLines = Files.readAllLines(Paths.get(fileName));

    for (String line : allLines) {
      System.out.println(line);
    }
    return allLines;
  }

  private static int calculateTotalShort(int noOfCom, int noOfNonCom){
    //=IF(L4<0;IF(K4<0;L4+K4;L4+(-K4));0)
    if (noOfCom<0){
      return noOfNonCom < 0  ?  noOfCom + noOfNonCom : noOfCom - noOfNonCom;
    }
    return 0;
  }

  private static String generateWeekString(LocalDateTime cotWeek) {
    return cotWeek.getYear() + "-" +
        (cotWeek.getMonthValue() < 10 ? "0" : "") + cotWeek.getMonthValue() +
        "-" + (cotWeek.getDayOfMonth() < 10 ? "0" : "") + cotWeek.getDayOfMonth();
  }

  private static boolean fileIsValid(List<String> cotLines, int enumLenght){
    return  enumLenght == cotLines.size()/numOfWeeksToLookBack;
  }

  public static void main(String[] args) throws IOException {
    long start = System.nanoTime();
    System.out.println("Date starting from:"+ startWeek.format(CUSTOM_FORMATTER));
    List<String> cotLines= readCotLinesFromFile(fileName);
    List<String> lines = new ArrayList<>();

    if (!fileIsValid(cotLines, IndexEnum.values().length)){
      lines = fetchAllCotLines();
    } else {
      lines = fetchOnlyTheCotOfLastWeek(cotLines);
    }

    writeToFile(lines, fileName);
    long end = System.nanoTime();
    // execution time
    long execution = (end - start)/(6000*10000000);
    System.out.println("Execution time: " + execution + " minutes");
  }

  private static List<String> fetchAllCotLines() {
    List<String> lines = new ArrayList<>();
    for (IndexEnum index : IndexEnum.values()) {
      System.out.println("Fetching cot of: " + index.indexName);

      for (int i = 0; i < numOfWeeksToLookBack; i++) {
        LocalDateTime cotWeekDateTime = startWeek.minusWeeks(i);
        String cotWeekString = generateWeekString(cotWeekDateTime);
        String url = generateFinalUrl(siteUrl, index.subUrl, cotWeekString);
//        System.out.println(url);
        try{
          Elements content = fetchContentFromSite(url);
          String line = generateWeeKCotReportLine(content, cotWeekDateTime, index);
          lines.add(line);
//          System.out.println(line);
        } catch (Exception e){
            lines.add(";;;;;;;;;");
            System.out.println(url);
//            System.out.println(";;;;;;;;;");
        }
      }
    }
    return lines;
  }

  private static List<String> fetchOnlyTheCotOfLastWeek(List<String> currentLines)
      throws IOException {
    String cotWeekString = generateWeekString(startWeek);
    if (new ArrayList<>(Arrays.asList(currentLines.get(0).split(";"))).get(0).endsWith(cotWeekString)){
      return currentLines;
    }

    List<String> lines = new ArrayList<>();
    List<String> cotLines= readCotLinesFromFile(fileName);
    for (IndexEnum index : IndexEnum.values()) {
      System.out.println("Fetching cot of: " + index.indexName);

       String url = generateFinalUrl(siteUrl, index.subUrl, cotWeekString);
        try{
          Elements content = fetchContentFromSite(url);
          String line = generateWeeKCotReportLine(content, startWeek, index);
          lines.add(line);
//          System.out.println(line);
        } catch (Exception e){
          lines.add(";;;;;;;;;");
//            System.out.println(url);
//            System.out.println(";;;;;;;;;");
        }
      }

    List<String> newLines = new ArrayList<>();
    for (int i=0; i < lines.size(); i++){
      newLines.add(lines.get(i));
      newLines.addAll(cotLines.subList(i*numOfWeeksToLookBack, (numOfWeeksToLookBack*(i+1))-1));
    }
    return newLines;
  }

  private static void writeToFile(List<String> lines, String fileName) throws IOException {
    Path filePath = Path.of("COTReports.txt");
    Files.write(filePath, lines, StandardCharsets.UTF_8);
  }

  private static Elements fetchContentFromSite(String url) throws IOException {
    String html = Jsoup.connect(url).get().html();
    Document doc = Jsoup.parse(html);
    return doc.getElementsByClass("number");
  }

  private static String generateFinalUrl(String siteUrl, String subUrl, String cotWeekString) {
    return siteUrl + subUrl + "/" + cotWeekString;
  }
}
