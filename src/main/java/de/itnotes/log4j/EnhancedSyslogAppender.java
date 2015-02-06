package de.itnotes.log4j;

import org.apache.log4j.Layout;
import org.apache.log4j.helpers.SyslogQuietWriter;
import org.apache.log4j.net.SyslogAppender;
import org.apache.log4j.spi.LoggingEvent;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * This is an enhanced version of the famous SyslogAppender
 * It let's you define
 * - the text that is used in the beginning of a splitted messge (e.g. define the tag name again)
 * - the maximum package size which can be very handy for modern syslog servers
 * - the header is always set to true
 *
 * @author Simon Dittlmann
 */
public class EnhancedSyslogAppender extends SyslogAppender {

    private String splitMessageBeginText;
    private String maxPackageSize = "1019";
    static final String TAB = "    ";

    // Have LOG_USER as default
    int syslogFacility = LOG_USER;
    String facilityStr;
    boolean facilityPrinting = false;

    //SyslogTracerPrintWriter stp;
    SyslogQuietWriter sqw;
    String syslogHost;

    /**
     * Date format used if header = true.
     * @since 1.2.15
     */
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd HH:mm:ss ", Locale.ENGLISH);
    /**
     * Host name used to identify messages from this appender.
     * @since 1.2.15
     */
    private String localHostname;

    /**
     * Set to true after the header of the layout has been sent or if it has none.
     */
    private boolean layoutHeaderChecked = false;

    private void splitPacket(final String header, final String packet) {
        int byteCount = packet.getBytes().length;

        if (byteCount <= Integer.getInteger(maxPackageSize)) {
            sqw.write(packet);
        } else {
            int split = header.length() + (packet.length() - header.length()) / 2;
            splitPacket(header, packet.substring(0, split) + "...");
            splitPacket(header, header + splitMessageBeginText + packet.substring(split));
        }
    }

    public void append(LoggingEvent event) {

        if(!isAsSevereAsThreshold(event.getLevel()))
            return;

        // We must not attempt to append if sqw is null.
        if(sqw == null) {
            errorHandler.error("No syslog host is set for SyslogAppedender named \""+
                    this.name+"\".");
            return;
        }

        if (!layoutHeaderChecked) {
            if (layout != null && layout.getHeader() != null) {
                sendLayoutMessage(layout.getHeader());
            }
            layoutHeaderChecked = true;
        }

        String hdr = getPacketHeader(event.timeStamp);
        String packet;
        if (layout == null) {
            packet = String.valueOf(event.getMessage());
        } else {
            packet = layout.format(event);
        }
        if(facilityPrinting || hdr.length() > 0) {
            StringBuilder buf = new StringBuilder(hdr);
            if(facilityPrinting) {
                buf.append(facilityStr);
            }
            buf.append(packet);
            packet = buf.toString();
        }

        sqw.setLevel(event.getLevel().getSyslogEquivalent());
        //
        //   if message has a remote likelihood of exceeding 1024 bytes
        //      when encoded, consider splitting message into multiple packets
        if (packet.length() > 256) {
            splitPacket(hdr, packet);
        } else {
            sqw.write(packet);
        }

        if (layout == null || layout.ignoresThrowable()) {
            String[] s = event.getThrowableStrRep();
            if (s != null) {
                for (String value : s) {
                    if (value.startsWith("\t")) {
                        sqw.write(hdr + TAB + value.substring(1));
                    } else {
                        sqw.write(hdr + value);
                    }
                }
            }
        }
    }

    public void setSplitMessageBeginText(String splitMessageBeginText) {
        this.splitMessageBeginText = splitMessageBeginText;
    }

    public void setMaxPackageSize(String maxPackageSize) {
        this.maxPackageSize = maxPackageSize;
    }

    public
    EnhancedSyslogAppender() {
        super();
    }

    public
    EnhancedSyslogAppender(Layout layout, int syslogFacility) {
        super(layout, syslogFacility);
    }

    public
    EnhancedSyslogAppender(Layout layout, String syslogHost, int syslogFacility) {
        super(layout, syslogHost, syslogFacility);
    }

    /**
     Release any resources held by this SyslogAppender.

     @since 0.8.4
     */
    synchronized
    public
    void close() {
        closed = true;
        if (sqw != null) {
            try {
                if (layoutHeaderChecked && layout != null && layout.getFooter() != null) {
                    sendLayoutMessage(layout.getFooter());
                }
                sqw.close();
                sqw = null;
            } catch(java.io.InterruptedIOException e) {
                Thread.currentThread().interrupt();
                sqw = null;
            } catch(IOException e) {
                sqw = null;
            }
        }
    }

    private
    void initSyslogFacilityStr() {
        facilityStr = getFacilityString(this.syslogFacility);

        if (facilityStr == null) {
            System.err.println("\"" + syslogFacility +
                    "\" is an unknown syslog facility. Defaulting to \"USER\".");
            this.syslogFacility = LOG_USER;
            facilityStr = "user:";
        } else {
            facilityStr += ":";
        }
    }

    /**
     Returns the specified syslog facility as a lower-case String,
     e.g. "kern", "user", etc.
     */
    public
    static
    String getFacilityString(int syslogFacility) {
        switch(syslogFacility) {
            case LOG_KERN:      return "kern";
            case LOG_USER:      return "user";
            case LOG_MAIL:      return "mail";
            case LOG_DAEMON:    return "daemon";
            case LOG_AUTH:      return "auth";
            case LOG_SYSLOG:    return "syslog";
            case LOG_LPR:       return "lpr";
            case LOG_NEWS:      return "news";
            case LOG_UUCP:      return "uucp";
            case LOG_CRON:      return "cron";
            case LOG_AUTHPRIV:  return "authpriv";
            case LOG_FTP:       return "ftp";
            case LOG_LOCAL0:    return "local0";
            case LOG_LOCAL1:    return "local1";
            case LOG_LOCAL2:    return "local2";
            case LOG_LOCAL3:    return "local3";
            case LOG_LOCAL4:    return "local4";
            case LOG_LOCAL5:    return "local5";
            case LOG_LOCAL6:    return "local6";
            case LOG_LOCAL7:    return "local7";
            default:            return null;
        }
    }



    /**
     * Get the host name used to identify this appender.
     * @return local host name
     * @since 1.2.15
     */
    private String getLocalHostname() {
        if (localHostname == null) {
            try {
                InetAddress addr = InetAddress.getLocalHost();
                localHostname = addr.getHostName();
            } catch (UnknownHostException uhe) {
                localHostname = "UNKNOWN_HOST";
            }
        }
        return localHostname;
    }

    /**
     * Gets HEADER portion of packet.
     * @param timeStamp number of milliseconds after the standard base time.
     * @return HEADER portion of packet, will be zero-length string if header is false.
     * @since 1.2.15
     */
    private String getPacketHeader(final long timeStamp) {
        /*
      If true, the appender will generate the HEADER (timestamp and host name)
      part of the syslog packet.
      */
        StringBuilder buf = new StringBuilder(dateFormat.format(new Date(timeStamp)));
        //  RFC 3164 says leading space, not leading zero on days 1-9
        if (buf.charAt(4) == '0') {
            buf.setCharAt(4, ' ');
        }
        buf.append(getLocalHostname());
        buf.append(' ');
        return buf.toString();
    }

    /**
     * Set header or footer of layout.
     * @param msg message body, may not be null.
     */
    private void sendLayoutMessage(final String msg) {
        if (sqw != null) {
            String packet = msg;
            String hdr = getPacketHeader(new Date().getTime());
            if(facilityPrinting || hdr.length() > 0) {
                StringBuilder buf = new StringBuilder(hdr);
                if(facilityPrinting) {
                    buf.append(facilityStr);
                }
                buf.append(msg);
                packet = buf.toString();
            }
            sqw.setLevel(6);
            sqw.write(packet);
        }
    }
}
