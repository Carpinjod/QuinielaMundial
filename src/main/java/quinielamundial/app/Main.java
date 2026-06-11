package quinielamundial.app;

public class Main {
    public static void main(String[] args) throws Exception {
        var port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        new QuinielaApp().start(port);
    }
}
