package com.venky.swf.plugins.collab.db.model.user;

import com.venky.core.random.Randomizer;
import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.ui.HIDDEN;
import com.venky.swf.routing.Config;

public interface OtpEnabled  {


    @HIDDEN
    public String getLastOtp();
    public void setLastOtp(String lastOtp);

    public void sendOtp();
    public void resendOtp();

    public void validateOtp(String otp);

    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    public boolean isValidated();
    public void setValidated(boolean validated);


    public static String generateOTP() {
        int otpLength = Config.instance().getIntProperty("swf.otp.length", 4);
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < otpLength; i++) {
            otp.append(Randomizer.getRandomNumber(i == 0 ? 1 : 0, 9));
        }
        return otp.toString();
    }

}