<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml"/>
  <xsl:template match="/NativeDicomModel">
    <Result>
      <xsl:choose>
        <xsl:when test="DicomAttribute[@tag='00080060']/Value = 'CT'">
          <xsl:choose>
            <xsl:when test="DicomAttribute[@tag='00180050']/Value &lt; 8">
                <Destination aet="THIN-SLICE-AET"/>
            </xsl:when>
            <xsl:otherwise>
                <Destination aet="THICK-SLICE-AET"/>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:when>
        <xsl:otherwise>
          <Destination aet="OTHER-AET"/>
        </xsl:otherwise>
      </xsl:choose>
    </Result>
  </xsl:template>
</xsl:stylesheet>
