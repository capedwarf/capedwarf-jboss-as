package org.jboss.as.capedwarf.extension;

import java.io.IOException;

import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;

/**
 * @author <a href="mailto:marko.luksa@gmail.com">Marko Luksa</a>
 * @author Tomaz Cerar
 * @author <a href="mailto:mlazar@redhat.com">Matej Lazar</a>
 */
public class SubsystemParsingTest extends AbstractSubsystemBaseTest {
    private static final String SUBSYSTEM_XML =
            "<subsystem xmlns=\"urn:jboss:domain:capedwarf:1.0\">\n" +
                    "            <appengine-api>abc123</appengine-api>\n" +
                    "            <admin-auth>false</admin-auth>\n" +
                    "         </subsystem>";

    public SubsystemParsingTest() {
        super(CapedwarfExtension.SUBSYSTEM_NAME, new CapedwarfExtension());
    }

    /**
     * Get the subsystem xml as string.
     *
     * @return the subsystem xml
     * @throws java.io.IOException
     */
    @Override
    protected String getSubsystemXml() throws IOException {
        return SUBSYSTEM_XML;
    }
}
