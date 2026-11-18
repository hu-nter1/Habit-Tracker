# Day by Day

Day by Day is a native Android habit and study tracker written in Java with
XML layouts and XML data storage. Its five destinations are Today, Habits,
Study, Review, and Learn. The interface combines a compact date-and-location
header, a softly animated forest-green backdrop, and high-contrast cards.
Original botanical details and the focus sprout keep the visual language
distinct while borrowing the calm, progress-centered mood of Forest.

## Build and install

From this folder, build and install the app with:

```sh
./android-app/build.sh
adb install -r android-app/build/StudyTracker.apk
adb shell am start -n com.personal.studytracker/.MainActivity
```

See [android-app/README.md](./android-app/README.md) for the Android project,
data format, features, and notification permissions.
