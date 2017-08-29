An Android application tracking and sending locations to [where-server](https://github.com/wujek-srujek/where-server).
Intended usage is to track location information during our sabbatical leave.

TODO:
- Use Google OAuth 2 (get token on the device, send with each request, validate on the server and fetch basic user info,
  base authorization on rules matching user name, email or whatever).
- Get rid of callback hell, use reactive programming.
- Implement foreground service with permission handling, notifications and correct Android Doze (battery optimizations)
  support.
- Generally, make the code better, it is crap right now.
- Rewrite in Kotlin, just to get some working experience with it.
