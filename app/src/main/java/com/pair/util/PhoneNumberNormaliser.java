package com.pair.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.util.regex.Pattern;

/**
 * @author Null-Pointer on 7/25/2015.
 */
public class PhoneNumberNormaliser {
    private static String TAG = PhoneNumberNormaliser.class.getSimpleName();

    private PhoneNumberNormaliser() {
        throw new IllegalStateException("cannot instantiate");
    }

    //rough pattern - 00********** or +*********** or 011********** or 166************* (166 is special for dialing us numbers from thailand)
    // any char that is not either + or digit is considered non-dialable
    private static final Pattern GLOBAL_NUMBER_PATTERN = Pattern.compile("^(00|011|166)"),
            NON_DIALABLE_PATTERN = Pattern.compile("[^\\d]");

    public static String toIEE(String phoneNumber, String defaultRegion) throws NumberParseException {
        if (phoneNumber == null) {
            throw new IllegalArgumentException("phoneNumber is null!");
        }
        if (defaultRegion == null) {
            throw new IllegalArgumentException("defaultRegion is null!");
        }
        PhoneNumberUtil utils = PhoneNumberUtil.getInstance();
        Phonenumber.PhoneNumber number = utils.parse(phoneNumber, defaultRegion);
        return number.getCountryCode() + "" /*convert to  string*/ + number.getNationalNumber();
    }

    public static boolean isIEE_Formatted(String phoneNumber, String region) {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        if (!phoneNumber.startsWith("+") && phoneNumber.startsWith("00") && phoneNumber.startsWith("011")) {
            phoneNumber = "+" + phoneNumber; // numbers like 233 20 4441069 will be parsed with no exception.
        }
        try {
            return util.isValidNumberForRegion(util.parse(phoneNumber, null), region);
        } catch (NumberParseException e) {
            return false;
        }
    }

    public static boolean isValidPhoneNumber(String number, String countryIso) {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        try {
            return util.isValidNumberForRegion(util.parse(number, countryIso), countryIso);
        } catch (NumberParseException e) {
            return false;
        }
    }

    public static String cleanNonDialableChars(String phoneNumber) {
        if (phoneNumber == null) {
            throw new IllegalArgumentException("phone number is null!");
        }
        boolean wasIEEFormatted = phoneNumber.indexOf('+') != -1;
        String ret = NON_DIALABLE_PATTERN.matcher(phoneNumber).replaceAll("");
        if (wasIEEFormatted) {
            ret = "+" + ret;
        }
        return ret;
    }

    public static String getCCC(String userCountryISO) {
        if (userCountryISO == null) throw new IllegalArgumentException("user country iso is null");
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        int ccc = util.getCountryCodeForRegion(userCountryISO);
        return ccc + "";
    }
}