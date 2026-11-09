package com.example.smartskan.ui

import androidx.compose.ui.res.stringResource
import com.example.smartskan.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

const val COMPANY_NAME = "Leisan LTD"
const val COMPANY_NUMBER = "14494525"
const val COMPANY_ADDRESS = "22 Holy Road, Kettering, NN16 9PN, United Kingdom"
const val CONTACT_EMAIL = "desmondtinarwo@gmail.com"
private const val POLICY_DATE = "8 November 2026"

class LegalSection(val heading: String, val body: String)

private val policy = listOf(
    LegalSection(
        "Who we are",
        "SmartSkan is published by $COMPANY_NAME, a company registered in England and Wales (company number " +
            "$COMPANY_NUMBER), $COMPANY_ADDRESS (\"we\", \"us\"). We are the data controller " +
            "for the limited personal data described below. You can contact us about privacy at $CONTACT_EMAIL.",
    ),
    LegalSection(
        "Your documents stay on your device",
        "Scans, imported PDFs, photos, extracted text, QR and barcode history, spending records and your saved " +
            "signature are stored only in the app's private storage on your device. We do not upload them to our " +
            "servers and we cannot see them. They are deleted when you delete them in the app or uninstall it.\n\n" +
            "Your documents, signature, QR and barcode history and spending records are left out of Google cloud " +
            "backup, so they won't come back if you restore from one. If you move to a new phone using Android's " +
            "direct device transfer, on Android 12 and above that transfer can copy them to the new phone. App " +
            "settings may be included in your Google backup, which is managed by Google under your Google account " +
            "settings, not by us.",
    ),
    LegalSection(
        "Scanning and text recognition",
        "Document scanning, QR/barcode scanning and text recognition use Google ML Kit and Google Play services. " +
            "This processing happens on your device; your images are not sent to us. Google Play services may " +
            "collect limited diagnostic and usage data under Google's Privacy Policy (policies.google.com/privacy).",
    ),
    LegalSection(
        "File access",
        "The app can only read files you choose yourself in Android's file picker, or photos you pick in Android's " +
            "photo picker. It doesn't ask for permission to access your storage, and files are never uploaded.",
    ),
    LegalSection(
        "Advertising",
        "The free version shows ads served by Google AdMob. To show and measure ads, Google may collect your " +
            "device's advertising ID, IP address, approximate location, device and app information, and how you " +
            "interact with ads.\n\n" +
            "In the UK, EEA and Switzerland we ask for your consent through Google's consent form before ads are " +
            "personalised. You can change your choice at any time in Settings › Ad privacy choices, and you can " +
            "reset or delete your advertising ID in your device settings. If you buy Remove ads, the app stops " +
            "requesting ads.\n\nLearn more: policies.google.com/technologies/ads",
    ),
    LegalSection(
        "Purchases",
        "The Remove ads purchase is processed by Google Play. We do not receive your payment card details. " +
            "Google tells the app whether the purchase was made so it can remove ads and restore them on a new " +
            "device. Google's handling of payment data is covered by the Google Payments Privacy Notice.",
    ),
    LegalSection(
        "If you contact us",
        "If you email us, we use your email address and the contents of your message only to reply and help " +
            "you. We keep this correspondence for up to 2 years, then delete it.",
    ),
    LegalSection(
        "Legal bases (UK GDPR)",
        "• Consent — personalised advertising (you can withdraw it at any time).\n" +
            "• Legitimate interests — showing non-personalised ads to fund the free app, preventing fraud and " +
            "keeping the app working.\n" +
            "• Contract — providing the Remove ads purchase.\n" +
            "• Legitimate interests — replying to your messages.",
    ),
    LegalSection(
        "Sharing and international transfers",
        "We do not sell your personal data. Data is shared only with Google (AdMob, Google Play and Google Play " +
            "services) as described above. Google may process data outside the UK; it uses approved safeguards " +
            "such as the UK International Data Transfer Addendum and Standard Contractual Clauses.",
    ),
    LegalSection(
        "Your rights",
        "Under UK data protection law you have the right to access, correct or delete your personal data, to " +
            "restrict or object to its use, to data portability, and to withdraw consent. Because your documents " +
            "are stored only on your device, you can view and delete them directly in the app. To exercise any " +
            "right, email $CONTACT_EMAIL.\n\n" +
            "You can also complain to the Information Commissioner's Office (ico.org.uk, 0303 123 1113).",
    ),
    LegalSection(
        "Children",
        "SmartSkan is not directed at children under 13, and we do not knowingly collect personal data from them.",
    ),
    LegalSection(
        "Changes to this policy",
        "We may update this policy from time to time. The latest version is always available in the app, " +
            "with the date it was last updated shown at the top.",
    ),
    LegalSection(
        "Contact",
        "$COMPANY_NAME (company number $COMPANY_NUMBER)\n$COMPANY_ADDRESS\n$CONTACT_EMAIL",
    ),
)

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    LegalDocumentScreen(stringResource(R.string.privacy_title), POLICY_DATE, policy, onBack)
}

/** A legal document. Only the title is translated: the wording that applies is the English text. */
@Composable
fun LegalDocumentScreen(title: String, lastUpdated: String, sections: List<LegalSection>, onBack: () -> Unit) {
    val english = LocalConfiguration.current.locales[0].language == "en"
    ScreenScaffold(title = title, onBack = onBack) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (!english) {
                item {
                    Text(stringResource(R.string.legal_english_only), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            item {
                Text(
                    "Last updated $lastUpdated",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(sections) { section ->
                Column {
                    Text(section.heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(section.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
