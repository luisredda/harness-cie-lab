package com.nikp.captcha;

import java.net.URI;
import java.util.HashMap;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.nikp.PaymentApplication;
import com.nikp.payment.infrastructure.exceptions.BankValidationException;
import com.nikp.payment.infrastructure.exceptions.ReCaptchaInvalidException;
import com.nikp.payment.infrastructure.exceptions.ReCaptchaUnavailableException;

//Step1: Import your Harness FF SDK dependencies here.
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.dto.Target;


@Service("captchaService")
public class CaptchaService extends AbstractCaptchaService {

    private final static Logger LOGGER = LoggerFactory.getLogger(CaptchaService.class);

//Step 2: Reference Cfclient here and fetch the target name from the enviroment variable harness.se
    @Autowired
    private CfClient cfClient;
 
    @Value("${harness.se}" )
    String targetName;
    
    @Override
    public void processResponse(final String response) {
	/**
	* Random Generator
	*
	*/
	Random r = new Random();
    	int low = 10;
    	int high = 100;
    	int randomNumber = r.nextInt(high-low) + low;
    	
    	System.out.println(randomNumber);
    	Object rn= (Integer) randomNumber;

	/**
	Step 3 - Insert the code below
	* Define you target on which you would like to evaluate the featureFlag 
	* Builds a target using specific key value pairs. This target can then be used by rules to evalue the flag
	*/
	
	HashMap<String,Object> map= new HashMap<>();
	        map.put("customerID", rn);
	        Target target = Target.builder()
	                .name(targetName)
	                .identifier(targetName)
	                .attributes(map)
	                .build();
	        
	boolean result =cfClient.boolVariation("bankvalidation", target, false);

       

    /**
        Step 4 - Include validation from FF
     *  This piece of code is the actual evaluation of the feature flag
     *  Uncomment the three lines below in order to start validating - Enable/Disable feature flag from the UI
     */
      if(result) {
      	   throw new BankValidationException("You have been selected for further bank validation, please call your bank");
       }
    	
        securityCheck(response);

        final URI verifyUri = URI.create(String.format(RECAPTCHA_URL_TEMPLATE, getReCaptchaSecret(), response, getClientIP()));
        System.out.println(verifyUri);
        
        
        
        try {
            final GoogleResponse googleResponse = restTemplate.getForObject(verifyUri, GoogleResponse.class);
            LOGGER.debug("Google's response: {} ", googleResponse.toString());

            if (!googleResponse.isSuccess()) {
                if (googleResponse.hasClientError()) {
                    reCaptchaAttemptService.reCaptchaFailed(getClientIP());
                }
                throw new ReCaptchaInvalidException("reCaptcha was not successfully validated");
            }
        } catch (RestClientException rce) {
            throw new ReCaptchaUnavailableException("Registration unavailable at this time.  Please try again later.", rce);
             
        }
        reCaptchaAttemptService.reCaptchaSucceeded(getClientIP());
    }
}
