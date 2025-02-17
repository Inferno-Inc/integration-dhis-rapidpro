/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.integration.rapidpro.security;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@Configuration
@ConditionalOnProperty( value = "webhook.security.auth", havingValue = "token" )
public class WebhookTokenAuthSecurityConfig
{
    protected static final Logger LOGGER = LoggerFactory.getLogger( WebhookTokenAuthSecurityConfig.class );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Bean
    protected SecurityFilterChain filterChain( HttpSecurity http )
        throws
        Exception
    {
        return http.antMatcher( "/dhis2rapidpro/webhook" )
            .csrf().disable()
            .formLogin().disable()
            .httpBasic().disable()
            .addFilterBefore( new TokenAuthenticationFilter( getOrGenerateToken() ),
                UsernamePasswordAuthenticationFilter.class )
            .authorizeRequests()
            .anyRequest().authenticated().and().build();
    }

    protected String getOrGenerateToken()
    {
        List<Map<String, Object>> token = jdbcTemplate.queryForList( "SELECT * FROM TOKEN" );
        String digest;
        if ( token.isEmpty() )
        {
            String value = new Base64StringKeyGenerator().generateKey();
            digest = Hashing.sha256().hashString( value, StandardCharsets.UTF_8 ).toString();

            jdbcTemplate.execute( String.format( "INSERT INTO TOKEN (value_) VALUES ('%s')", digest ) );
            LOGGER.warn( String.format(
                "%n%nUsing generated token for authenticating webhook messages from RapidPro: %s%n%nThis token cannot be recovered so it should be kept safe and secure. "
                    + "This message will NOT appear again unless you truncate the table 'TOKEN' to generate a new token.%n",
                value ) );
        }
        else
        {
            digest = (String) token.get( 0 ).get( "VALUE_" );
        }

        return digest;
    }
}