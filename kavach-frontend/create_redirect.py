import os

portfolio_dir = r"c:\Users\Kanak\OneDrive\Desktop\Final_Resume\kanak-portfolio"
redirect_file = os.path.join(portfolio_dir, "resume-drive.html")

html_content = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Kanak Verma - Resume</title>
  <meta name="description" content="Android and Full-Stack Developer | Kotlin, Jetpack Compose, Kavach">

  <!-- Open Graph for LinkedIn Preview -->
  <meta property="og:title" content="Kanak Verma - Resume">
  <meta property="og:description" content="Android and Full-Stack Developer | Kotlin, Jetpack Compose, Kavach">
  <meta property="og:type" content="website">

  <!-- Immediate redirect to Google Drive file -->
  <meta http-equiv="refresh" content="0; url=https://drive.google.com/file/d/1TQs03g_4Kbrt6UAxx5_dhUzrUKOBkD1w/view?usp=sharing">
  <script>
    window.location.replace("https://drive.google.com/file/d/1TQs03g_4Kbrt6UAxx5_dhUzrUKOBkD1w/view?usp=sharing");
  </script>
</head>
<body style="font-family: sans-serif; text-align: center; padding: 50px;">
  <p>Opening Resume on Google Drive... <a href="https://drive.google.com/file/d/1TQs03g_4Kbrt6UAxx5_dhUzrUKOBkD1w/view?usp=sharing">Click here if not redirected</a></p>
</body>
</html>"""

if os.path.exists(portfolio_dir):
    with open(redirect_file, 'w', encoding='utf-8') as f:
        f.write(html_content)
    print("Created resume-drive.html successfully")
