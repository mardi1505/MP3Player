# MP3Player
Allows the user to add MP3 URLs and play/pause them via a ListView and media control buttons

## Description
Android Java code that demonstrates the following:
> - Activity with UI controls to add MP3 URLs to a SQLite database and uses a SimpleCursorAdapter to populate the ListView
> - Uses Regular Expressions to validate the MP3 URL entered by the user and extracts the file name from the URL
> - Allows users to play and pause by toggling the selection a ListView item or using the media control ImageButtons
> - Users a Foreground Service to play the MP3 URLs and automatically plays the next track in the database
> - AsyncTask is used to prepare the MP3 and detects errors caused by either i) No Internet Connectivity, ii) Invalid URL or iii) Invalid MP3 file and displays a Toast while the MP3PlayerActivity is in the foreground
> - Intents are used to communicate requests and notifications between the Activity and Service
> - SharedPreferences are used to persist states