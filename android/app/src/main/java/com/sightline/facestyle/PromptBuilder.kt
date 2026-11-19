package com.sightline.facestyle

/** Builds the instruction sent alongside the images. */
object PromptBuilder {

    fun build(faceCount: Int, keepMyHair: Boolean, extra: String, forChatApp: Boolean = false): String {
        val personImages = if (faceCount == 1) "IMAGE 2" else "IMAGES 2–${faceCount + 1}"
        val hair = if (keepMyHair) {
            "Use the person's own hairstyle, hair colour and hairline from $personImages, " +
                "lit and colour-graded to match the reference."
        } else {
            "Keep the hairstyle, hair styling and accessories exactly as they are in the reference."
        }
        return buildString {
            if (forChatApp) {
                appendLine("I've attached ${faceCount + 1} images in order. IMAGE 1 is the reference photo; the other image(s) are photos of me. Please generate an image.")
                appendLine()
            }
            appendLine("Task: recreate IMAGE 1 as a new photograph in which the person from $personImages takes the place of the person in IMAGE 1.")
            appendLine()
            appendLine("Copy everything from IMAGE 1 as exactly as possible:")
            appendLine("- composition, framing, camera angle, lens look and depth of field")
            appendLine("- pose, body position, gesture, outfit, fabrics, jewellery and props")
            appendLine("- background, setting and every environmental detail")
            appendLine("- lighting direction, light quality, shadows and highlights")
            appendLine("- colour palette, colour grading, contrast, saturation and white balance")
            appendLine("- textures: film grain, noise, softness or sharpness, any vintage or retro processing")
            appendLine("- overall mood, atmosphere and era of the image")
            appendLine()
            appendLine("Take the identity ONLY from $personImages:")
            appendLine("- face shape, jawline, eyes, eyebrows, nose, lips, ears, skin tone and distinguishing features (moles, freckles, facial hair)")
            appendLine("- the result must be clearly and instantly recognisable as this same person; do not beautify, slim, age or alter their features")
            appendLine("- match the facial expression and head angle of the person in IMAGE 1")
            appendLine("- $hair")
            appendLine("- blend skin naturally into the reference's lighting and grading so it looks like one real, unedited photograph")
            appendLine()
            appendLine("Do not include any trace of the original face from IMAGE 1. Do not add text, borders, watermarks or collage layouts. Output a single photorealistic image.")
            if (extra.isNotBlank()) {
                appendLine()
                appendLine("Additional instructions from the user: ${extra.trim()}")
            }
        }
    }
}
