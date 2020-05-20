package com.commonjava.reptoro.common;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.contrib.json.classic.JsonLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;


public class CustomJsonLayout extends JsonLayout {
    public static final String ENVIRONMENT = "environment";

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private String environmentMappings;

    private Map<String, String> envars;

    public String getEnvironmentMappings()
    {
        return environmentMappings;
    }

    public void setEnvironmentMappings( final String environmentMappings )
    {
        this.environmentMappings = environmentMappings;

        String[] mappings = environmentMappings == null ? new String[0] : environmentMappings.split( "\\s*,\\s*" );
        envars = new HashMap<>();
        Stream.of(mappings).forEach(kv ->{
            String[] keyAlias = kv.split( "\\s*=\\s*" );
            if ( keyAlias.length > 1 )
            {
                String value = System.getenv( keyAlias[0].trim() );
                if (Objects.isNull(value) || value.isEmpty() )
                {
                    value = "Unknown";
                }

                envars.put( keyAlias[1].trim(), value );
            }
        } );
    }

    @Override
    protected void addCustomDataToJsonMap( Map<String, Object> map, ILoggingEvent iLoggingEvent )
    {
        super.addCustomDataToJsonMap( map, iLoggingEvent );

        map.put( ENVIRONMENT, envars );
    }
}