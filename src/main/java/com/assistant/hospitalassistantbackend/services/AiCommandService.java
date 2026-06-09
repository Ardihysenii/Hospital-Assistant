package com.assistant.hospitalassistantbackend.services;

import com.assistant.hospitalassistantbackend.models.Patient;
import com.assistant.hospitalassistantbackend.repositories.PatientRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class AiCommandService {

    private final PatientRepository patientRepository;

    // --- STATE MACHINE MEMORY FIELDS ---
    private String currentStep = "IDLE"; // Tracks: IDLE, AWAITING_DOB, AWAITING_BLOOD
    private String tempFirstName = "";
    private String tempLastName = "";
    private LocalDate tempDob = null;

    public AiCommandService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    public String executeVoiceCommand(String rawCommand) {
        if (rawCommand == null || rawCommand.trim().isEmpty()) {
            return "Vocal stream matrix was empty.";
        }

        System.out.println("CURRENT CONVERSATION STEP: [" + currentStep + "] | INCOMING TEXT: \"" + rawCommand + "\"");
        String lowerCommand = rawCommand.toLowerCase().trim();

        // Check if the user wants to hard-reset the conversation wizard at any time
        if (lowerCommand.contains("cancel") || lowerCommand.contains("restart process")) {
            resetWizard();
            return "Intake wizard reset. Standing by for new commands.";
        }

        switch (currentStep) {

            case "IDLE":
                // STEP 1: Capture the name boundaries
                if (lowerCommand.contains("patient") || lowerCommand.contains("add") || lowerCommand.contains("register")) {
                    int index = -1;
                    String keywordUsed = "";

                    if (lowerCommand.contains("named ")) { keywordUsed = "named "; index = lowerCommand.indexOf(keywordUsed); }
                    else if (lowerCommand.contains("name ")) { keywordUsed = "name "; index = lowerCommand.indexOf(keywordUsed); }
                    else if (lowerCommand.contains("called ")) { keywordUsed = "called "; index = lowerCommand.indexOf(keywordUsed); }
                    else if (lowerCommand.contains("patient ")) { keywordUsed = "patient "; index = lowerCommand.indexOf(keywordUsed); }

                    if (index == -1) {
                        return "Patient intent noted, but I couldn't isolate the name parameters. Try saying: 'Add patient named Ardi'.";
                    }

                    String rawName = rawCommand.substring(index + keywordUsed.length()).trim();
                    if (rawName.endsWith(".")) rawName = rawName.substring(0, rawName.length() - 1).trim();

                    if (rawName.isEmpty()) return "Parsing error: Isolated name field was empty.";

                    // Parse name structure strings
                    String[] nameParts = rawName.split("\\s+");
                    this.tempFirstName = nameParts[0].substring(0, 1).toUpperCase() + nameParts[0].substring(1).toLowerCase();

                    // FIXED: Changed from "VoiceIntake" to "" to pass DB constraints without showing up in the UI
                    this.tempLastName = "";

                    if (nameParts.length > 1) {
                        this.tempLastName = nameParts[1].substring(0, 1).toUpperCase() + nameParts[1].substring(1).toLowerCase();
                    }

                    // Shift tracking phase to Date of Birth intake
                    this.currentStep = "AWAITING_DOB";
                    return "System captured identity: [" + this.tempFirstName + "]. Now, what is the birth date for " + this.tempFirstName + "? (Please state numbers or month name)";
                }
                return "Command unhandled. Speak 'Add a patient named...' to engage the medical register wizard.";

            case "AWAITING_DOB":
                // STEP 2: Process the spoken numbers/date parameters
                try {
                    this.tempDob = parseSpokenDate(rawCommand);

                    // Advance to Blood Group parsing phase next
                    this.currentStep = "AWAITING_BLOOD";
                    return "Birth date logged: " + this.tempDob + ". Final configuration step: Which Blood Group is " + this.tempFirstName + "?";
                } catch (Exception e) {
                    return "Could not compute date format from: \"" + rawCommand + "\". Please say it clearly, for example: 'January 1 2000' or '15 04 1994'.";
                }

            case "AWAITING_BLOOD":
                // STEP 3: Validate Blood Class and commit record fields directly to PostgreSQL
                String parsedBlood = parseBloodGroup(rawCommand);
                if (parsedBlood == null) {
                    return "Unrecognized biological classification matrix. Please clearly state a valid blood group, like: 'A positive', 'O negative', or 'B plus'.";
                }

                try {
                    Patient finalPatient = new Patient();
                    finalPatient.setFirstName(this.tempFirstName);
                    finalPatient.setLastName(this.tempLastName);
                    finalPatient.setDateOfBirth(this.tempDob);
                    finalPatient.setBloodGroup(parsedBlood);

                    // Commit to active Neon DB instance
                    Patient saved = patientRepository.save(finalPatient);

                    // Wipe local wizard memory arrays clean
                    resetWizard();

                    // FIXED: Added .trim() so "Ardi " displays beautifully as "Ardi" if no last name exists
                    String fullFormattedName = (saved.getFirstName() + " " + saved.getLastName()).trim();

                    return "Data matching complete! Patient successfully committed: [" + fullFormattedName + " | DOB: " + saved.getDateOfBirth() + " | Blood Group: " + saved.getBloodGroup() + "] inside PostgreSQL.";
                } catch (Exception e) {
                    resetWizard();
                    return "Critical infrastructure crash: Postgres rejected database transaction logic layers.";
                }

            default:
                resetWizard();
                return "System tracking pipeline variance detected. Resetting wizard framework context elements.";
        }
    }

    private void resetWizard() {
        this.currentStep = "IDLE";
        this.tempFirstName = "";
        this.tempLastName = "";
        this.tempDob = null;
    }

    // --- SMART PARSING HELPER UTILITIES ---

    private LocalDate parseSpokenDate(String input) {
        String lower = input.toLowerCase().trim();
        String[] monthsList = {"january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"};

        int month = -1;
        for (int i = 0; i < monthsList.length; i++) {
            if (lower.contains(monthsList[i])) {
                month = i + 1;
                lower = lower.replace(monthsList[i], " ");
                break;
            }
        }

        // Clean up conversational suffixes transcription engines like to throw in
        lower = lower.replaceAll("first", "1").replaceAll("second", "2").replaceAll("third", "3")
                .replaceAll("th", "").replaceAll("st", "").replaceAll("nd", "").replaceAll("rd", "");

        // Isolate digit character fields only
        String digits = lower.replaceAll("[^0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        String[] tokens = digits.split(" ");

        if (month != -1 && tokens.length >= 2) {
            int day = Integer.parseInt(tokens[0]);
            int year = Integer.parseInt(tokens[1]);
            if (year < 100) year += 2000;
            return LocalDate.of(year, month, day);
        } else if (tokens.length >= 3) {
            // Evaluates text streams like "01 01 2000" or "12 04 1995"
            int val1 = Integer.parseInt(tokens[0]);
            int val2 = Integer.parseInt(tokens[1]);
            int year = Integer.parseInt(tokens[2]);
            if (year < 100) year += 2000;

            int day = val1;
            int m = val2;
            if (m > 12) { // Auto-corrects if text flows as Month-Day instead of Day-Month
                day = val2;
                m = val1;
            }
            return LocalDate.of(year, m, day);
        }
        throw new IllegalArgumentException("Incomplete date data coordinates.");
    }

    private String parseBloodGroup(String input) {
        String clean = input.toLowerCase()
                .replaceAll("positive", "+").replaceAll("negative", "-")
                .replaceAll("plus", "+").replaceAll("minus", "-")
                .replaceAll("\\s+", "").toUpperCase();

        if (clean.contains("A+") || clean.contains("A-") || clean.contains("B+") || clean.contains("B-") ||
                clean.contains("AB+") || clean.contains("AB-") || clean.contains("O+") || clean.contains("O-")) {

            // Extract match specifically
            if (clean.contains("A+")) return "A+";
            if (clean.contains("A-")) return "A-";
            if (clean.contains("B+")) return "B+";
            if (clean.contains("B-")) return "B-";
            if (clean.contains("AB+")) return "AB+";
            if (clean.contains("AB-")) return "AB-";
            if (clean.contains("O+")) return "O+";
            if (clean.contains("O-")) return "O-";
        }
        return null;
    }
}