/*
* Copyright (C) 2011 The Libphonenumber Authors
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
* @author Shaopeng Jia
*/
package com.google.phonenumbers
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale.ENGLISH
import org.apache.commons.fileupload.FileItemIterator
import org.apache.commons.fileupload.FileItemStream
import org.apache.commons.fileupload.FileUploadException
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.fileupload.util.Streams
import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringEscapeUtils
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.Locale
import java.util.StringTokenizer
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
/**
* A servlet that accepts requests that contain strings representing a phone number and a default
* country, and responds with results from parsing, validating and formatting the number. The
* default country is a two-letter region code representing the country that we are expecting the
* number to be from.
*/
class PhoneNumberParserServlet:HttpServlet() {
  private val phoneUtil = PhoneNumberUtil.getInstance()
  private val shortInfo = ShortNumberInfo.getInstance()
  @Throws(IOException::class)
  fun doPost(req:HttpServletRequest, resp:HttpServletResponse) {
    val phoneNumber:String = null
    val defaultCountry:String = null
    val languageCode = "en" // Default languageCode to English if nothing is entered.
    val regionCode = ""
    val fileContents:String = null
    val upload = ServletFileUpload()
    upload.setSizeMax(50000)
    try
    {
      val iterator = upload.getItemIterator(req)
      while (iterator.hasNext())
      {
        val item = iterator.next()
        val `in` = item.openStream()
        if (item.isFormField())
        {
          val fieldName = item.getFieldName()
          if (fieldName == "phoneNumber")
          {
            phoneNumber = Streams.asString(`in`, UTF_8.name())
          }
          else if (fieldName == "defaultCountry")
          {
            defaultCountry = Streams.asString(`in`).toUpperCase()
          }
          else if (fieldName == "languageCode")
          {
            val languageEntered = Streams.asString(`in`).toLowerCase()
            if (languageEntered.length > 0)
            {
              languageCode = languageEntered
            }
          }
          else if (fieldName == "regionCode")
          {
            regionCode = Streams.asString(`in`).toUpperCase()
          }
        }
        else
        {
          try
          {
            fileContents = IOUtils.toString(`in`)
          }
          finally
          {
            IOUtils.closeQuietly(`in`)
          }
        }
      }
    }
    catch (e1:FileUploadException) {
      e1.printStackTrace()
    }
    val output:StringBuilder
    resp.setContentType("text/html")
    resp.setCharacterEncoding(UTF_8.name())
    if (fileContents == null || fileContents.length == 0)
    {
      // Redirect to a URL with the given input encoded in the query parameters.
      val geocodingLocale = Locale(languageCode, regionCode)
      resp.sendRedirect(getPermaLinkURL(phoneNumber, defaultCountry, geocodingLocale,
                                        false /* absoluteURL */))
    }
    else
    {
      resp.getWriter().println(getOutputForFile(defaultCountry, fileContents))
    }
  }
  /**
 * Handle the get request to get information about a number based on query parameters.
 */
  @Throws(IOException::class)
  fun doGet(req:HttpServletRequest, resp:HttpServletResponse) {
    val phoneNumber = req.getParameter("number")
    if (phoneNumber == null)
    {
      phoneNumber = ""
    }
    val defaultCountry = req.getParameter("country")
    if (defaultCountry == null)
    {
      defaultCountry = ""
    }
    val geocodingParam = req.getParameter("geocodingLocale")
    val geocodingLocale:Locale
    if (geocodingParam == null)
    {
      geocodingLocale = ENGLISH // Default languageCode to English if nothing is entered.
    }
    else
    {
      geocodingLocale = Locale.forLanguageTag(geocodingParam)
    }
    resp.setContentType("text/html")
    resp.setCharacterEncoding(UTF_8.name())
    resp.getWriter().println(
      getOutputForSingleNumber(phoneNumber, defaultCountry, geocodingLocale))
  }
  private fun getOutputForFile(defaultCountry:String, fileContents:String):StringBuilder {
    val output = StringBuilder(
      "<HTML><HEAD><TITLE>Results generated from phone numbers in the file provided:" + "</TITLE></HEAD><BODY>")
    output.append("<TABLE align=center border=1>")
    output.append("<TH align=center>ID</TH>")
    output.append("<TH align=center>Raw phone number</TH>")
    output.append("<TH align=center>Pretty formatting</TH>")
    output.append("<TH align=center>International format</TH>")
    val phoneNumberId = 0
    val tokenizer = StringTokenizer(fileContents, ",")
    while (tokenizer.hasMoreTokens())
    {
      val numberStr = tokenizer.nextToken()
      phoneNumberId++
      output.append("<TR>")
      output.append("<TD align=center>").append(phoneNumberId).append(" </TD> \n")
      output.append("<TD align=center>").append(
        StringEscapeUtils.escapeHtml(numberStr)).append(" </TD> \n")
      try
      {
        val number = phoneUtil.parseAndKeepRawInput(numberStr, defaultCountry)
        val isNumberValid = phoneUtil.isValidNumber(number)
        val prettyFormat = if (isNumberValid)
        phoneUtil.formatInOriginalFormat(number, defaultCountry)
        else
        "invalid"
        val internationalFormat = if (isNumberValid)
        phoneUtil.format(number, PhoneNumberFormat.INTERNATIONAL)
        else
        "invalid"
        output.append("<TD align=center>").append(
          StringEscapeUtils.escapeHtml(prettyFormat)).append(" </TD> \n")
        output.append("<TD align=center>").append(
          StringEscapeUtils.escapeHtml(internationalFormat)).append(" </TD> \n")
      }
      catch (e:NumberParseException) {
        output.append("<TD align=center colspan=2>").append(
          StringEscapeUtils.escapeHtml(e.toString())).append(" </TD> \n")
      }
      output.append("</TR>")
    }
    output.append("</BODY></HTML>")
    return output
  }
  private fun appendLine(title:String, data:String, output:StringBuilder) {
    output.append("<TR>")
    output.append("<TH>").append(title).append("</TH>")
    output.append("<TD>").append(if (data.length > 0) data else "&nbsp;").append("</TD>")
    output.append("</TR>")
  }
  /**
 * Returns a stable URL pointing to the result page for the given input.
 */
  private fun getPermaLinkURL(
    phoneNumber:String, defaultCountry:String, geocodingLocale:Locale, absoluteURL:Boolean):String {
    // If absoluteURL is false, generate a relative path. Otherwise, produce an absolute URL.
    val permaLink = StringBuilder(
      if (absoluteURL) "http://libphonenumber.appspot.com/phonenumberparser" else "/phonenumberparser")
    try
    {
      permaLink.append(
        "?number=" + URLEncoder.encode(if (phoneNumber != null) phoneNumber else "", UTF_8.name()))
      if (defaultCountry != null && !defaultCountry.isEmpty())
      {
        permaLink.append("&country=" + URLEncoder.encode(defaultCountry, UTF_8.name()))
      }
      if (geocodingLocale.getLanguage() != ENGLISH.getLanguage() || !geocodingLocale.getCountry().isEmpty())
      {
        permaLink.append("&geocodingLocale=" + URLEncoder.encode(geocodingLocale.toLanguageTag(), UTF_8.name()))
      }
    }
    catch (e:UnsupportedEncodingException) {
      // UTF-8 is guaranteed in Java, so this should be impossible.
      throw AssertionError(e)
    }
    return permaLink.toString()
  }
  /**
 * Returns a link to create a new github issue with the relevant information.
 */
  private fun getNewIssueLink(
    phoneNumber:String, defaultCountry:String, geocodingLocale:Locale):String {
    val hasDefaultCountry = !defaultCountry.isEmpty() && defaultCountry !== "ZZ"
    val issueTitle = "Validation issue with " + phoneNumber
    + (if (hasDefaultCountry) " (" + defaultCountry + ")" else "")
    val issueTemplate = StringBuilder(
      "Please read the \"guidelines for contributing\" (linked above) and fill " + "in the template below.\n\n")
    issueTemplate.append("Country/region affected (e.g., \"US\"): ")
    .append(defaultCountry).append("\n\n")
    issueTemplate.append("Example number(s) affected (\"+1 555 555-1234\"): ")
    .append(phoneNumber).append("\n\n")
    issueTemplate.append(
      "The phone number range(s) to which the issue applies (\"+1 555 555-XXXX\"): \n\n")
    issueTemplate.append(
      "The type of the number(s) (\"fixed-line\", \"mobile\", \"short code\", etc.): \n\n")
    issueTemplate.append(
      "The cost, if applicable (\"toll-free\", \"premium rate\", \"shared cost\"): \n\n")
    issueTemplate.append(
      "Supporting evidence (for example, national numbering plan, announcement from mobile "
      + "carrier, news article): **IMPORTANT - anything posted here is made public. "
      + "Read the guidelines first!** \n\n")
    issueTemplate.append("[link to demo]("
                         + getPermaLinkURL(phoneNumber, defaultCountry, geocodingLocale, true /* absoluteURL */)
                         + ")\n\n")
    val newIssueLink = "https://github.com/googlei18n/libphonenumber/issues/new?title="
    try
    {
      newIssueLink += URLEncoder.encode(issueTitle, UTF_8.name()) + "&body="
      + URLEncoder.encode(issueTemplate.toString(), UTF_8.name())
    }
    catch (e:UnsupportedEncodingException) {
      // UTF-8 is guaranteed in Java, so this should be impossible.
      throw AssertionError(e)
    }
    return newIssueLink
  }
  /**
 * The defaultCountry here is used for parsing phoneNumber. The geocodingLocale is used to specify
 * the language used for displaying the area descriptions generated from phone number geocoding.
 */
  private fun getOutputForSingleNumber(
    phoneNumber:String, defaultCountry:String, geocodingLocale:Locale):StringBuilder {
    val output = StringBuilder("<HTML><HEAD>")
    output.append(
      "<LINK type=\"text/css\" rel=\"stylesheet\" href=\"/stylesheets/main.css\" />")
    output.append("</HEAD>")
    output.append("<BODY>")
    output.append("Phone Number entered: " + StringEscapeUtils.escapeHtml(phoneNumber) + "<BR>")
    output.append("defaultCountry entered: " + StringEscapeUtils.escapeHtml(defaultCountry)
                  + "<BR>")
    output.append("Language entered: "
                  + StringEscapeUtils.escapeHtml(geocodingLocale.toLanguageTag()) + "<BR>")
    try
    {
      val number = phoneUtil.parseAndKeepRawInput(phoneNumber, defaultCountry)
      output.append("<DIV>")
      output.append("<TABLE border=1>")
      output.append("<TR><TD colspan=2>Parsing Result (parseAndKeepRawInput())</TD></TR>")
      appendLine("country_code", Integer.toString(number.getCountryCode()), output)
      appendLine("national_number", java.lang.Long.toString(number.getNationalNumber()), output)
      appendLine("extension", number.getExtension(), output)
      appendLine("country_code_source", number.getCountryCodeSource().toString(), output)
      appendLine("italian_leading_zero", java.lang.Boolean.toString(number.isItalianLeadingZero()), output)
      appendLine("raw_input", number.getRawInput(), output)
      output.append("</TABLE>")
      output.append("</DIV>")
      val isPossible = phoneUtil.isPossibleNumber(number)
      val isNumberValid = phoneUtil.isValidNumber(number)
      val numberType = phoneUtil.getNumberType(number)
      val hasDefaultCountry = !defaultCountry.isEmpty() && defaultCountry !== "ZZ"
      output.append("<DIV>")
      output.append("<TABLE border=1>")
      output.append("<TR><TD colspan=2>Validation Results</TD></TR>")
      appendLine("Result from isPossibleNumber()", java.lang.Boolean.toString(isPossible), output)
      if (!isPossible)
      {
        appendLine("Result from isPossibleNumberWithReason()",
                   phoneUtil.isPossibleNumberWithReason(number).toString(), output)
        output.append("<TR><TD colspan=2>Note: numbers that are not possible have type " + "UNKNOWN, an unknown region, and are considered invalid.</TD></TR>")
      }
      else
      {
        appendLine("Result from isValidNumber()", java.lang.Boolean.toString(isNumberValid), output)
        if (isNumberValid)
        {
          if (hasDefaultCountry)
          {
            appendLine(
              "Result from isValidNumberForRegion()",
              java.lang.Boolean.toString(phoneUtil.isValidNumberForRegion(number, defaultCountry)),
              output)
          }
        }
        val region = phoneUtil.getRegionCodeForNumber(number)
        appendLine("Phone Number region", if (region == null) "" else region, output)
        appendLine("Result from getNumberType()", numberType.toString(), output)
      }
      output.append("</TABLE>")
      output.append("</DIV>")
      if (!isNumberValid)
      {
        output.append("<DIV>")
        output.append("<TABLE border=1>")
        output.append("<TR><TD colspan=2>Short Number Results</TD></TR>")
        val isPossibleShort = shortInfo.isPossibleShortNumber(number)
        appendLine("Result from isPossibleShortNumber()",
                   java.lang.Boolean.toString(isPossibleShort), output)
        if (isPossibleShort)
        {
          appendLine("Result from isValidShortNumber()",
                     java.lang.Boolean.toString(shortInfo.isValidShortNumber(number)), output)
          if (hasDefaultCountry)
          {
            val isPossibleShortForRegion = shortInfo.isPossibleShortNumberForRegion(number, defaultCountry)
            appendLine("Result from isPossibleShortNumberForRegion()",
                       java.lang.Boolean.toString(isPossibleShortForRegion), output)
            if (isPossibleShortForRegion)
            {
              appendLine("Result from isValidShortNumberForRegion()",
                         java.lang.Boolean.toString(shortInfo.isValidShortNumberForRegion(number,
                                                                                          defaultCountry)), output)
            }
          }
        }
        output.append("</TABLE>")
        output.append("</DIV>")
      }
      output.append("<DIV>")
      output.append("<TABLE border=1>")
      output.append("<TR><TD colspan=2>Formatting Results</TD></TR>")
      appendLine("E164 format",
                 if (isNumberValid) phoneUtil.format(number, PhoneNumberFormat.E164) else "invalid",
                 output)
      appendLine("Original format",
                 phoneUtil.formatInOriginalFormat(number, defaultCountry), output)
      appendLine("National format", phoneUtil.format(number, PhoneNumberFormat.NATIONAL), output)
      appendLine(
        "International format",
        if (isNumberValid) phoneUtil.format(number, PhoneNumberFormat.INTERNATIONAL) else "invalid",
        output)
      appendLine(
        "Out-of-country format from US",
        if (isNumberValid) phoneUtil.formatOutOfCountryCallingNumber(number, "US") else "invalid",
        output)
      appendLine(
        "Out-of-country format from CH",
        if (isNumberValid) phoneUtil.formatOutOfCountryCallingNumber(number, "CH") else "invalid",
        output)
      output.append("</TABLE>")
      output.append("</DIV>")
      val formatter = phoneUtil.getAsYouTypeFormatter(defaultCountry)
      val rawNumberLength = phoneNumber.length
      output.append("<DIV>")
      output.append("<TABLE border=1>")
      output.append("<TR><TD colspan=2>AsYouTypeFormatter Results</TD></TR>")
      for (i in 0..rawNumberLength - 1)
      {
        // Note this doesn't handle supplementary characters, but it shouldn't be a big deal as
        // there are no dial-pad characters in the supplementary range.
        val inputChar = phoneNumber.get(i)
        appendLine("Char entered: '" + inputChar + "' Output: ",
                   formatter.inputDigit(inputChar), output)
      }
      output.append("</TABLE>")
      output.append("</DIV>")
      if (isNumberValid)
      {
        output.append("<DIV>")
        output.append("<TABLE border=1>")
        output.append("<TR><TD colspan=2>PhoneNumberOfflineGeocoder Results</TD></TR>")
        appendLine(
          "Location",
          PhoneNumberOfflineGeocoder.getInstance().getDescriptionForNumber(
            number, geocodingLocale),
          output)
        output.append("</TABLE>")
        output.append("</DIV>")
        output.append("<DIV>")
        output.append("<TABLE border=1>")
        output.append("<TR><TD colspan=2>PhoneNumberToTimeZonesMapper Results</TD></TR>")
        appendLine(
          "Time zone(s)",
          PhoneNumberToTimeZonesMapper.getInstance().getTimeZonesForNumber(number).toString(),
          output)
        output.append("</TABLE>")
        output.append("</DIV>")
        if (numberType === PhoneNumberType.MOBILE ||
            numberType === PhoneNumberType.FIXED_LINE_OR_MOBILE ||
            numberType === PhoneNumberType.PAGER)
        {
          output.append("<DIV>")
          output.append("<TABLE border=1>")
          output.append("<TR><TD colspan=2>PhoneNumberToCarrierMapper Results</TD></TR>")
          appendLine(
            "Carrier",
            PhoneNumberToCarrierMapper.getInstance().getNameForNumber(number, geocodingLocale),
            output)
          output.append("</TABLE>")
          output.append("</DIV>")
        }
      }
      val newIssueLink = getNewIssueLink(phoneNumber, defaultCountry, geocodingLocale)
      val guidelinesLink = "/"
      output.append("<b style=\"color:red\">File an issue</b>: by clicking on "
                    + "<a target=\"_blank\" href=\"" + newIssueLink + "\">this link</a>, I confirm that I "
                    + "have read the <a target=\"_blank\" href=\"" + guidelinesLink
                    + "\">contributor's guidelines</a>.")
    }
    catch (e:NumberParseException) {
      output.append(StringEscapeUtils.escapeHtml(e.toString()))
    }
    output.append("</BODY></HTML>")
    return output
  }
}
