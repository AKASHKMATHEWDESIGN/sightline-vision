# FaceStyle Studio (Android)

Recreate any reference photo with **your** face. The app copies the reference image's pose,
outfit, background, lighting, colour grading, texture and mood, and puts your face in place
of the original person.

It uses Google's Gemini image models ("Nano Banana") through the Gemini API.

## Free vs paid

- **Free: open in Gemini app**: no API key needed. The app writes the detailed prompt, copies it
  to your clipboard and sends your photos plus the reference into Google's Gemini app, which
  generates the image for free (daily limits apply). Install Gemini from the Play Store first.
- **Generate in app**: needs a Gemini API key **with billing enabled**. Google's free API tier
  has a limit of 0 for image models (you'll see `429 ... limit: 0` without billing).
  Costs a few cents per image.

## How to use

1. Get a Gemini API key at https://aistudio.google.com/apikey. Image generation may require
   billing to be enabled on that Google project.
2. Install the APK (see below), open the app and paste the key into **Settings** (gear icon).
3. **Your photos**: add 1–3 clear photos of your face. Several angles give better likeness.
4. **Reference image**: pick the picture whose look you want.
5. Optionally turn on *Keep my own hairstyle* or add extra instructions.
6. Confirm the consent checkbox, tap **Generate**, then **Save** (to Pictures/FaceStyle) or **Share**.
   Each run gives a different result; tap **Try again** if the likeness isn't right.

## Models (Settings)

| Model ID | Notes |
| --- | --- |
| `gemini-3.1-flash-image` | Nano Banana 2, the default. Tuned for consistency across multiple reference images. |
| `gemini-nano-banana-2.1` | Newest fast model |
| `gemini-3-pro-image` | Nano Banana Pro: highest detail, slower and costs more |
| `gemini-2.5-flash-image` | The original Nano Banana (legacy) |

The model ID field is editable, so you can enter newer model IDs as Google releases them.

## Getting the APK

Every push that changes `android/` runs the **Android APK** GitHub Actions workflow.
Open the repo's **Actions** tab, open the latest run and download the
`FaceStyleStudio-debug-apk` artifact. Unzip it, copy `app-debug.apk` to your phone and open it.
Allow "install unknown apps" if Android asks.

To build locally with Android Studio: open the `android/` folder and run the `app` configuration,
or run `./gradlew assembleDebug`.

## Privacy

Your photos go straight from the phone to Google's Gemini API to create the image. Nothing goes
to any other server. Google's Gemini API terms apply to that data. The API key is stored in the
app's private storage on the device. Generated images carry Google's invisible SynthID watermark.
Only use faces of people who have agreed to it.
