package pro.sketchware.firebase;

/** Synthetic google-services.json files for the tests: the real shape, obviously made-up identifiers. */
public final class GoogleServicesConfigTestFixtures {
    private GoogleServicesConfigTestFixtures() {
    }

    public static final String KEY = "AIza" + "A".repeat(35);
    public static final String KEY_2 = "AIza" + "B".repeat(35);

    public static String file(String pkg) {
        return """
                {
                  "project_info": {
                    "project_number": "123456789012",
                    "firebase_url": "https://demo-app-12345-default-rtdb.firebaseio.com",
                    "project_id": "demo-app-12345",
                    "storage_bucket": "demo-app-12345.appspot.com"
                  },
                  "client": [
                    {
                      "client_info": {
                        "mobilesdk_app_id": "1:123456789012:android:0123456789abcdef",
                        "android_client_info": { "package_name": "%s" }
                      },
                      "oauth_client": [
                        { "client_id": "123456789012-android.apps.googleusercontent.com", "client_type": 1 },
                        { "client_id": "123456789012-web.apps.googleusercontent.com", "client_type": 3 }
                      ],
                      "api_key": [ { "current_key": "%s" } ]
                    }
                  ],
                  "configuration_version": "1"
                }
                """.formatted(pkg, KEY);
    }

    public static String twoClients() {
        return """
                {
                  "project_info": { "project_number": "123456789012", "project_id": "demo-app-12345",
                                    "firebase_url": "https://demo-app-12345-default-rtdb.firebaseio.com",
                                    "storage_bucket": "demo-app-12345.appspot.com" },
                  "client": [
                    { "client_info": { "mobilesdk_app_id": "1:123456789012:android:aaaaaaaaaaaaaaaa",
                                       "android_client_info": { "package_name": "com.demo.first" } },
                      "api_key": [ { "current_key": "%s" } ] },
                    { "client_info": { "mobilesdk_app_id": "1:123456789012:android:bbbbbbbbbbbbbbbb",
                                       "android_client_info": { "package_name": "com.demo.second" } },
                      "api_key": [ { "current_key": "%s" } ] }
                  ]
                }
                """.formatted(KEY, KEY_2);
    }

}
