<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml"/>
  <xsl:template match="/NativeDicomModel">
    <Result>
      <xsl:choose>
        <xsl:when test="DicomAttribute[@tag='00080060']/Value = 'MR'">
          <Destination aet="AET1"/>
          <Destination aet="AET2"/>
        </xsl:when>
        <xsl:otherwise>
          <Destination aet="AET3"/>
        </xsl:otherwise>
      </xsl:choose>
    </Result>
  </xsl:template>
</xsl:stylesheet>
