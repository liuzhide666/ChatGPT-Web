package android.content;

public class Context {
    public static final int MODE_PRIVATE = 0;
    public static final String CONNECTIVITY_SERVICE = "connectivity";
    private final Context applicationContext;
    public int applicationContextCalls;

    public Context() {
        this.applicationContext = this;
    }

    public Context(Context applicationContext) {
        this.applicationContext = applicationContext;
    }

    public Context getApplicationContext() {
        applicationContextCalls++;
        return applicationContext;
    }
}
