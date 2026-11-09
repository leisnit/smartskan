package com.example.smartskan.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.smartskan.R

private const val TERMS_DATE = "9 November 2026"

private val terms = listOf(
    LegalSection(
        "About these terms",
        "These terms apply when you use the SmartSkan app. SmartSkan is published by $COMPANY_NAME, a company " +
            "registered in England and Wales (company number $COMPANY_NUMBER), $COMPANY_ADDRESS (\"we\", \"us\"). " +
            "By installing or using the app you agree to these terms. If you don't agree, please don't use the " +
            "app. How we handle personal data is explained separately in our Privacy Policy, in Settings.",
    ),
    LegalSection(
        "Your licence",
        "We give you a personal, non-exclusive, non-transferable licence to use SmartSkan on Android devices you " +
            "own or control, for as long as you follow these terms. The app, its design and its name belong to us " +
            "or our licensors. You may not copy, resell or reverse engineer the app, except where the law gives " +
            "you a right to do so that can't be excluded.",
    ),
    LegalSection(
        "Age",
        "You must be at least 13 to use SmartSkan. If you are under 18, please make sure a parent or guardian " +
            "agrees to these terms and to any purchase.",
    ),
    LegalSection(
        "Your documents and using the app lawfully",
        "You keep all rights in what you scan, import or create. We don't receive it and claim no rights over it.\n\n" +
            "You are responsible for what you do with the app. Only scan, copy, sign or share documents you are " +
            "entitled to use. Identity documents, passports and other people's personal details need particular " +
            "care: some countries restrict copying them, and data protection law may apply if you handle other " +
            "people's data. Do not use SmartSkan to forge or alter documents, to impersonate anyone, or for " +
            "anything else that is unlawful or infringes someone's rights.",
    ),
    LegalSection(
        "Keep your own copies",
        "Your documents, signature, scan history and spending records are stored only on your device and are " +
            "left out of Google cloud backup. We have no copy and cannot recover them. If you delete them, " +
            "uninstall the app, or lose or reset your device, they may be lost for good. Please keep your own " +
            "copies of anything important.",
    ),
    LegalSection(
        "Automatic results may contain mistakes",
        "Text recognition, PDF to Word conversion, passport and ID detail reading, barcode reading and receipt " +
            "reading are automated and can be wrong or incomplete. Check the results before you rely on them. " +
            "Details read from a passport or ID card, including the \"verified by check digits\" message, do not " +
            "confirm that a document is genuine or valid.\n\n" +
            "The Spending tool is a personal record-keeping aid. It is not financial, tax or accounting advice.",
    ),
    LegalSection(
        "Signatures",
        "The sign tool places a drawing of your signature on a PDF. It is not a certified or qualified electronic " +
            "signature, and it doesn't verify anyone's identity. Whether a signed document is accepted or legally " +
            "effective depends on the law and on who receives it, so check first if it matters. We don't give " +
            "legal advice.",
    ),
    LegalSection(
        "Ads and the Remove ads purchase",
        "The free version of SmartSkan shows ads served by Google AdMob. Remove ads is a one-time purchase, sold " +
            "and processed through Google Play, at the price shown before you buy. It removes ads on devices using " +
            "the same Google account while the app remains available, and you can restore it from Settings. Google " +
            "Play's terms and refund policies also apply to the payment. A purchase only removes ads; it doesn't " +
            "add a support plan or guarantee future features.",
    ),
    LegalSection(
        "Other companies' services",
        "Scanning, text recognition, ads and purchases rely on services from Google, including Google Play " +
            "services, ML Kit, AdMob and Google Play. Those services are provided by Google under its own terms. " +
            "If one of them is unavailable on your device, some features may not work.",
    ),
    LegalSection(
        "Changes and availability",
        "We may update the app, change or remove features, or stop offering it. We aim to keep SmartSkan working " +
            "well, but we can't promise it will always be available, free of errors or compatible with every " +
            "device or file. Some PDFs can't be opened, for example if they are damaged or password-protected.",
    ),
    LegalSection(
        "Our responsibility to you",
        "Nothing in these terms limits your legal rights as a consumer, or our liability for death or personal " +
            "injury caused by negligence, for fraud, or for anything else that can't legally be limited. If you " +
            "bought Remove ads and it doesn't work as described, you may have rights under consumer law, " +
            "including the right to a repair, replacement or refund.\n\n" +
            "Subject to that, we are not responsible for loss that wasn't reasonably foreseeable, for loss of " +
            "documents or data you didn't keep your own copies of, or for losses caused by relying on automatic " +
            "results without checking them. If you use the app for business, we are not liable for lost profit, " +
            "lost business or business interruption, and our total liability to business users is limited to the " +
            "amount paid for Remove ads.",
    ),
    LegalSection(
        "Ending these terms",
        "You can stop using SmartSkan at any time by uninstalling it. Uninstalling deletes the documents stored " +
            "in the app. We may end your licence if you seriously or repeatedly break these terms, in which case " +
            "you must stop using the app.",
    ),
    LegalSection(
        "Changes to these terms",
        "We may update these terms from time to time, for example to reflect changes in the app or the law. The " +
            "latest version is always available in the app, with its date shown at the top. If you keep using " +
            "SmartSkan after a change, the updated terms apply.",
    ),
    LegalSection(
        "Law and disputes",
        "These terms are governed by the law of England and Wales, and disputes may be brought in the courts of " +
            "England and Wales. If you live in Scotland or Northern Ireland, you can also bring proceedings there. " +
            "If you live elsewhere, you keep any mandatory consumer protections of the country you live in, and " +
            "can use its courts where that law allows. If any part of these terms can't be enforced, the rest " +
            "still applies.",
    ),
    LegalSection(
        "Contact",
        "$COMPANY_NAME (company number $COMPANY_NUMBER)\n$COMPANY_ADDRESS\n$CONTACT_EMAIL",
    ),
)

@Composable
fun TermsOfUseScreen(onBack: () -> Unit) {
    LegalDocumentScreen(stringResource(R.string.terms_title), TERMS_DATE, terms, onBack)
}
