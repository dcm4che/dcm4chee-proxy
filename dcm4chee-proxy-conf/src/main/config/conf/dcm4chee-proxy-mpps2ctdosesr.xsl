<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0" xml:space="preserve">
  <!-- Required Parameters -->
  <xsl:param name="IrradiationEventUID" />
  <xsl:param name="DeviceObserverUID" />
  <xsl:param name="PerfomedProcedureStepSOPInstanceUID" />
  <xsl:template match="/">
    <!-- 
      Configure:
      * 'Procedure Intent'
      * 'CT Acquisition Type'
      * 'Scanning Length'
      * 'CT Dose Length Product Total'
      * 'Nominal Single Collimation Width'
      * 'Nominal Total Collimation Width'
      * 'Number of X-Ray Sources'
      * 'Identification Number of the X-Ray Source'
      * 'CTDIw Phantom Type'
      according to Procedure, Modality and MPPS content 
    -->
    <NativeDicomModel xml-space="preserved">
      <DicomAttribute keyword="SOPClassUID" tag="00080016" vr="UI">
        <Value number="1">1.2.840.10008.5.1.4.1.1.88.67</Value>
      </DicomAttribute>
      <!-- SOPInstanceUID will be generated and set by code using this xsl -->
      <DicomAttribute keyword="StudyDate" tag="00080020" vr="DA">
        <Value number="1">
          <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='00400244']/Value" />
        </Value>
      </DicomAttribute>
      <DicomAttribute keyword="ContentDate" tag="00080023" vr="DA">
        <Value number="1">
          <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='00400244']/Value" />
        </Value>
      </DicomAttribute>
      <DicomAttribute keyword="StudyTime" tag="00080030" vr="TM">
        <Value number="1">
          <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='00400245']/Value" />
        </Value>
      </DicomAttribute>
      <DicomAttribute keyword="ContentTime" tag="00080033" vr="TM">
        <Value number="1">
          <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='00400245']/Value" />
        </Value>
      </DicomAttribute>
      <DicomAttribute keyword="AccessionNumber" tag="00080050" vr="SH">
        <Value number="1">
          <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='00400270']/Item[1]/DicomAttribute[@tag='00080050']/Value" />
        </Value>
      </DicomAttribute>
      <DicomAttribute keyword="Modality" tag="00080060" vr="CS">
        <Value number="1">
          <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='00080060']/Value" />
        </Value>
      </DicomAttribute>
      <DicomAttribute keyword="Manufacturer" tag="00080070" vr="LO">
        <Value number="1">
          <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='00080070']/Value" />
        </Value>
      </DicomAttribute>
      <DicomAttribute keyword="ManufacturerModelName" tag="00081090" vr="LO">
        <Value number="1">
          <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='00081090']/Value" />
        </Value>
      </DicomAttribute>
      <DicomAttribute keyword="ReferringPhysicianName" tag="00080090" vr="PN">
        <xsl:copy-of select="/NativeDicomModel/DicomAttribute[@tag='00080090']/PersonName[1]" />
      </DicomAttribute>
      <DicomAttribute keyword="ReferencedPerformedProcedureStepSequence" tag="00081111" vr="SQ">
        <xsl:copy-of select="/NativeDicomModel/DicomAttribute[@tag='00081111']/Item[1]" />
      </DicomAttribute>
      <DicomAttribute keyword="PatientName" tag="00100010" vr="PN">
        <xsl:copy-of select="/NativeDicomModel/DicomAttribute[@tag='00100010']/PersonName[1]" />
      </DicomAttribute>
      <DicomAttribute keyword="PatientID" tag="00100020" vr="LO">
        <Value number="1">
          <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='00100020']/Value" />
        </Value>
      </DicomAttribute>
      <DicomAttribute keyword="PatientBirthDate" tag="00100030" vr="DA">
        <Value number="1">
          <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='00100030']/Value" />
        </Value>
      </DicomAttribute>
      <DicomAttribute keyword="PatientSex" tag="00100040" vr="CS">
        <Value number="1">
          <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='00100040']/Value" />
        </Value>
      </DicomAttribute>
      <DicomAttribute keyword="DeviceSerialNumber" tag="00181000" vr="LO">
        <Value number="1">
          <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='00181000']/Value" />
        </Value>
      </DicomAttribute>
      <DicomAttribute keyword="SoftwareVersions" tag="00181020" vr="LO">
        <Value number="1">
          <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='00181020']/Value" />
        </Value>
      </DicomAttribute>
      <DicomAttribute keyword="StudyInstanceUID" tag="0020000D" vr="UI">
        <Value number="1">
          <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='00400270']/Item[1]/DicomAttribute[@tag='0020000D']/Value" />
        </Value>
      </DicomAttribute>
      <DicomAttribute keyword="StudyID" tag="00200010" vr="SH">
        <Value number="1">
          <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='00200010']/Value" />
        </Value>
      </DicomAttribute>
      <!-- SeriesInstanceUID is generated and set in Java method using this xsl -->
      <DicomAttribute keyword="SeriesNumber" tag="00200011" vr="IS">
        <Value number="1">1</Value>
      </DicomAttribute>
      <DicomAttribute keyword="InstanceNumber" tag="00200013" vr="SH">
        <Value number="1">1</Value>
      </DicomAttribute>
      <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
        <Value number="1">CONTAINER</Value>
      </DicomAttribute>
      <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
        <Item number="1">
          <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
            <Value number="1">113701</Value>
          </DicomAttribute>
          <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
            <Value number="1">DCM</Value>
          </DicomAttribute>
          <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
            <Value number="1">X-Ray Radiation Dose Report</Value>
          </DicomAttribute>
        </Item>
      </DicomAttribute>
      <DicomAttribute keyword="ContinuityOfContent" tag="0040A050" vr="CS">
        <Value number="1">SEPARATE</Value>
      </DicomAttribute>
      <DicomAttribute keyword="CompletionFlag" tag="0040A491" vr="CS">
        <Value number="1">PARTIAL</Value>
      </DicomAttribute>
      <DicomAttribute keyword="VerificationFlag" tag="0040A493" vr="CS">
        <Value number="1">UNVERIFIED</Value>
      </DicomAttribute>
      <DicomAttribute keyword="ContentTemplateSequence" tag="0040A504" vr="SQ">
        <Item number="1">
          <DicomAttribute keyword="MappingResource" tag="00080105" vr="CS">
            <Value number="1">DCMR</Value>
          </DicomAttribute>
          <DicomAttribute keyword="TemplateIdentifier" tag="0040DB00" vr="CS">
            <Value number="1">10011</Value>
          </DicomAttribute>
        </Item>
      </DicomAttribute>
      <DicomAttribute keyword="ContentSequence" tag="0040A730" vr="SQ">
        <Item number="1">
          <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
            <Value number="1">HAS CONCEPT MOD</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
            <Value number="1">CODE</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                <Value number="1">121058</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                <Value number="1">DCM</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                <Value number="1">Procedure reported</Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
          <DicomAttribute keyword="ConceptCodeSequence" tag="0040A168" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                <Value number="1">P5-08000</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                <Value number="1">SRT</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                <Value number="1">Computed Tomography X-Ray</Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
          <DicomAttribute keyword="ContentSequence" tag="0040A730" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
                <Value number="1">HAS CONCEPT MOD</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
                <Value number="1">CODE</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">G-C0E8</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">SRT</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">Has Intent</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              <DicomAttribute keyword="ConceptCodeSequence" tag="0040A168" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">R-408C3</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">SRT</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">Diagnostic Intent</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              <!-- Other Procedure Intent -->
              <!--  
              <DicomAttribute keyword="ConceptCodeSequence" tag="0040A168" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">R-41531</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">SRT</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">Therapeutic Intent</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              -->
              <!--
              <DicomAttribute keyword="ConceptCodeSequence" tag="0040A168" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">R-002E9</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">SRT</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">Combined Diagnostic and Therapeutic Procedure</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              -->
            </Item>
          </DicomAttribute>
        </Item>
        <Item number="2">
          <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
            <Value number="1">HAS OBS CONTEXT</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
            <Value number="1">UIDREF</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                <Value number="1">121012</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                <Value number="1">DCM</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                <Value number="1">Device Observer UID</Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
          <DicomAttribute keyword="UID" tag="0040A124" vr="UI">
            <Value number="1">
              <xsl:value-of select="$DeviceObserverUID" />
            </Value>
          </DicomAttribute>
        </Item>
        <Item number="3">
          <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
            <Value number="1">HAS OBS CONTEXT</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
            <Value number="1">DATETIME</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                <Value number="1">113809</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                <Value number="1">DCM</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                <Value number="1">Start of X-Ray Irradiation</Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
          <DicomAttribute keyword="DateTime" tag="0040A120" vr="DT">
            <Value number="1">
              <xsl:value-of select="concat(/NativeDicomModel/DicomAttribute[@tag='00400244']/Value, /NativeDicomModel/DicomAttribute[@tag='00400245']/Value)" />
            </Value>
          </DicomAttribute>
        </Item>
        <Item number="4">
          <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
            <Value number="1">HAS OBS CONTEXT</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
            <Value number="1">DATETIME</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                <Value number="1">113810</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                <Value number="1">DCM</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                <Value number="1">End of X-Ray Irradiation</Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
          <DicomAttribute keyword="DateTime" tag="0040A120" vr="DT">
            <Value number="1">
              <xsl:value-of select="concat(/NativeDicomModel/DicomAttribute[@tag='00400250']/Value, /NativeDicomModel/DicomAttribute[@tag='00400251']/Value)" />
            </Value>
          </DicomAttribute>
        </Item>
        <Item number="5">
          <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
            <Value number="1">HAS OBS CONTEXT</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
            <Value number="1">CODE</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                <Value number="1">113705</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                <Value number="1">DCM</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                <Value number="1">Scope of Accumulation</Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
          <DicomAttribute keyword="ConceptCodeSequence" tag="0040A168" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                <Value number="1">113016</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                <Value number="1">DCM</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                <Value number="1">Performed Procedure Step</Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
          <DicomAttribute keyword="ContentSequence" tag="0040A730" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
                <Value number="1">HAS PROPERTIES</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
                <Value number="1">UIDREF</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A168" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">121126</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">DCM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">Performed Procedure Step SOP Instance UID</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              <DicomAttribute keyword="UID" tag="0040A124" vr="UI">
                <Value number="1">
                  <xsl:value-of
                    select="$PerfomedProcedureStepSOPInstanceUID" />
                </Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
        </Item>
        <Item number="6">
          <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
            <Value number="1">CONTAINS</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
            <Value number="1">CONTAINER</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                <Value number="1">113811</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                <Value number="1">DCM</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                <Value number="1">CT Accumulated Dose Data</Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
          <DicomAttribute keyword="ContinuityOfContent" tag="0040A050" vr="CS">
            <Value number="1">SEPARATE</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ContentSequence" tag="0040A730" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
                <Value number="1">CONTAINS</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
                <Value number="1">NUM</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">113812</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">DCM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">Total Number of Irradiation Events</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              <DicomAttribute keyword="MeasuredValueSequence" tag="0040A300" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="MeasurementUnitsCodeSequence" tag="004008EA"
                    vr="SQ">
                    <Item number="1">
                      <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                        <Value number="1">{events}</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102"
                        vr="SH">
                        <Value number="1">UCUM</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                        <Value number="1">events</Value>
                      </DicomAttribute>
                    </Item>
                  </DicomAttribute>
                  <DicomAttribute keyword="NumericValue" tag="0040A30A" vr="DS">
                    <Value number="1"><xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='00400301']/Value"/></Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
            </Item>
            <Item number="2">
              <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
                <Value number="1">CONTAINS</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
                <Value number="1">NUM</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">113813</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">DCM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">CT Dose Length Product Total</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              <DicomAttribute keyword="MeasuredValueSequence" tag="0040A300" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="MeasurementUnitsCodeSequence" tag="004008EA"
                    vr="SQ">
                    <Item number="1">
                      <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                        <Value number="1">mGy.cm</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102"
                        vr="SH">
                        <Value number="1">UCUM</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                        <Value number="1">mGy.cm</Value>
                      </DicomAttribute>
                    </Item>
                  </DicomAttribute>
                  <DicomAttribute keyword="NumericValue" tag="0040A30A" vr="DS">
                    <Value number="1">
                      <xsl:choose>
                        <xsl:when test="/NativeDicomModel/DicomAttribute[@tag='0018115E']/Value">
                          <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='0018115E']/Value div 100000"/>
                        </xsl:when>
                        <xsl:when test="/NativeDicomModel/DicomAttribute[@tag='00400302']/Value">
                          <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='00400302']/Value * /NativeDicomModel/DicomAttribute[@tag='00400303']/Value"/>
                        </xsl:when>
                        <xsl:otherwise>
                          <!-- This fallback method needs to be adjusted to the format of 'CommentsOnRadiationDose' of the particular CT -->
                          <xsl:value-of select="substring-after(/NativeDicomModel/DicomAttribute[@tag='00400310']/Value[1], 'Total DLP=')"/>
                        </xsl:otherwise>
                      </xsl:choose>
                    </Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
        </Item> 
        <Item number="7">
          <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
            <Value number="1">CONTAINS</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
            <Value number="1">CONTAINER</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                <Value number="1">113819</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                <Value number="1">DCM</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                <Value number="1">CT Acquisition</Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
          <DicomAttribute keyword="ContinuityOfContent" tag="0040A050" vr="CS">
            <Value number="1">SEPARATE</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ContentSequence" tag="0040A730" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
                <Value number="1">CONTAINS</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
                <Value number="1">CODE</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">123014</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">DCM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">Target Region</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              <DicomAttribute keyword="ConceptCodeSequence" tag="0040A168" vr="SQ">
                <xsl:choose>
                  <xsl:when test="/NativeDicomModel/DicomAttribute[@tag='00081032']/Item[1]">
                    <xsl:copy-of select="/NativeDicomModel/DicomAttribute[@tag='00081032']/Item[1]" />
                  </xsl:when>
                  <xsl:when test="/NativeDicomModel/DicomAttribute[@tag='00400270']/Item[1]/DicomAttribute[@tag='00400008']/Item[1]">
                    <xsl:copy-of select="/NativeDicomModel/DicomAttribute[@tag='00400270']/Item[1]/DicomAttribute[@tag='00400008']/Item[1]" />
                  </xsl:when>
                  <xsl:otherwise>
                    <Item number="1">
                      <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                        <Value number="1">T-D0010</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                        <Value number="1">SRT</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                        <Value number="1">Entire body</Value>
                      </DicomAttribute>
                    </Item>
                  </xsl:otherwise>
                </xsl:choose>
              </DicomAttribute>
            </Item>
            <Item number="2">
              <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
                <Value number="1">CONTAINS</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
                <Value number="1">CODE</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">113820</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">DCM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">CT Acquisition Type</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              <DicomAttribute keyword="ConceptCodeSequence" tag="0040A168" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">113807</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">DCM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">Free Acquisition</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              <!-- Other Acquisition Types -->
              <!--
              <DicomAttribute keyword="ConceptCodeSequence" tag="0040A168" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">P5-08001</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">SRT</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">SPIRAL</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              -->
              <!--
              <DicomAttribute keyword="ConceptCodeSequence" tag="0040A168" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">113805</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">DCM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">Constant Angle Acquisition</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              -->
              <!--
              <DicomAttribute keyword="ConceptCodeSequence" tag="0040A168" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">113806</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">DCM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">Stationary Acquisition</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              -->
              <!--
              <DicomAttribute keyword="ConceptCodeSequence" tag="0040A168" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">113804</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">DCM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">Sequenced Acquisition</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              -->
            </Item>
            <Item number="4">
              <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
                <Value number="1">CONTAINS</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
                <Value number="1">UIDREF</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">113769</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">DCM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">Irradiation Event UID</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              <DicomAttribute keyword="UID" tag="0040A124" vr="UI">
                <Value number="1">$IrradiationEventUID</Value>
              </DicomAttribute>
            </Item>
            <Item number="6">
              <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
                <Value number="1">CONTAINS</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
                <Value number="1">CONTAINER</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">113822</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">DCM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">CT Acquisition Parameters</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              <DicomAttribute keyword="ContinuityOfContent" tag="0040A050" vr="CS">
                <Value number="1">SEPARATE</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ContentSequence" tag="0040A730" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
                    <Value number="1">CONTAINS</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
                    <Value number="1">NUM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
                    <Item number="1">
                      <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                        <Value number="1">113824</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                        <Value number="1">DCM</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                        <Value number="1">Exposure Time</Value>
                      </DicomAttribute>
                    </Item>
                  </DicomAttribute>
                  <DicomAttribute keyword="MeasuredValueSequence" tag="0040A300" vr="SQ">
                    <Item number="1">
                      <DicomAttribute keyword="MeasurementUnitsCodeSequence" tag="004008EA" vr="SQ">
                        <Item number="1">
                          <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                            <Value number="1">s</Value>
                          </DicomAttribute>
                          <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                            <Value number="1">UCUM</Value>
                          </DicomAttribute>
                          <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                            <Value number="1">s</Value>
                          </DicomAttribute>
                        </Item>
                      </DicomAttribute>
                      <DicomAttribute keyword="NumericValue" tag="0040A30A" vr="DS">
                        <Value number="1"><xsl:value-of select="sum(/NativeDicomModel/DicomAttribute[@tag='0040030E']/child::*/DicomAttribute[@tag='00181150']/Value)"/></Value>
                      </DicomAttribute>
                    </Item>
                  </DicomAttribute>
                </Item>
                <Item number="2">
                  <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
                    <Value number="1">CONTAINS</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
                    <Value number="1">NUM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
                    <Item number="1">
                      <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                        <Value number="1">113825</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                        <Value number="1">DCM</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                        <Value number="1">Scanning Length</Value>
                      </DicomAttribute>
                    </Item>
                  </DicomAttribute>
                  <DicomAttribute keyword="MeasuredValueSequence" tag="0040A300" vr="SQ">
                    <Item number="1">
                      <DicomAttribute keyword="MeasurementUnitsCodeSequence" tag="004008EA" vr="SQ">
                        <Item number="1">
                          <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                            <Value number="1">mm</Value>
                          </DicomAttribute>
                          <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                            <Value number="1">UCUM</Value>
                          </DicomAttribute>
                          <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                            <Value number="1">mm</Value>
                          </DicomAttribute>
                        </Item>
                      </DicomAttribute>
                      <DicomAttribute keyword="NumericValue" tag="0040A30A" vr="DS">
                        <!--  
                        For Spiral scanning, the scanning length is normally the table travel in mm during the tube
                        loading (see DICOM PS 3.16 2011 Figure A-16).
                        
                        For Sequenced scanning, the scanning length is the table travel between consecutive
                        scans times the number of scans.
                        
                        For Stationary and Free scanning, the scanning length is the nominal width of the total
                        collimation.
                        -->
                        <Value number="1">0</Value>
                      </DicomAttribute>
                    </Item>
                  </DicomAttribute>
                </Item>
                <Item number="3">
                  <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
                    <Value number="1">CONTAINS</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
                    <Value number="1">NUM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
                    <Item number="1">
                      <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                        <Value number="1">113826</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102"
                        vr="SH">
                        <Value number="1">DCM</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                        <Value number="1">Nominal Single Collimation Width</Value>
                      </DicomAttribute>
                    </Item>
                  </DicomAttribute>
                  <DicomAttribute keyword="MeasuredValueSequence" tag="0040A300" vr="SQ">
                    <Item number="1">
                      <DicomAttribute keyword="MeasurementUnitsCodeSequence" tag="004008EA" vr="SQ">
                        <Item number="1">
                          <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                            <Value number="1">mm</Value>
                          </DicomAttribute>
                          <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                            <Value number="1">UCUM</Value>
                          </DicomAttribute>
                          <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                            <Value number="1">mm</Value>
                          </DicomAttribute>
                        </Item>
                      </DicomAttribute>
                      <DicomAttribute keyword="NumericValue" tag="0040A30A" vr="DS">
                        <!-- 
                          The value of the nominal width (referenced to the location of the isocenter along the z
                          axis) of a single collimated slice in mm.
                        -->
                        <Value number="1">0</Value>
                      </DicomAttribute>
                    </Item>
                  </DicomAttribute>
                </Item>
                <Item number="4">
                  <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
                    <Value number="1">CONTAINS</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
                    <Value number="1">NUM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
                    <Item number="1">
                      <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                        <Value number="1">113827</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102"
                        vr="SH">
                        <Value number="1">DCM</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                        <Value number="1">Nominal Total Collimation Width</Value>
                      </DicomAttribute>
                    </Item>
                  </DicomAttribute>
                  <DicomAttribute keyword="MeasuredValueSequence" tag="0040A300" vr="SQ">
                    <Item number="1">
                      <DicomAttribute keyword="MeasurementUnitsCodeSequence" tag="004008EA" vr="SQ">
                        <Item number="1">
                          <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                            <Value number="1">mm</Value>
                          </DicomAttribute>
                          <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                            <Value number="1">UCUM</Value>
                          </DicomAttribute>
                          <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                            <Value number="1">mm</Value>
                          </DicomAttribute>
                        </Item>
                      </DicomAttribute>
                      <DicomAttribute keyword="NumericValue" tag="0040A30A" vr="DS">
                        <!--  
                          The value of the nominal width (referenced to the location of the isocenter along the z
                          axis) of the nominal total collimation in mm over the area of active X-Ray detection (z-
                          coverage).
                        -->
                        <Value number="1">0</Value>
                      </DicomAttribute>
                    </Item>
                  </DicomAttribute>
                </Item>
                <Item number="5">
                  <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
                    <Value number="1">CONTAINS</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
                    <Value number="1">NUM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
                    <Item number="1">
                      <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                        <Value number="1">113823</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102"
                        vr="SH">
                        <Value number="1">DCM</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                        <Value number="1">Number of X-Ray Sources</Value>
                      </DicomAttribute>
                    </Item>
                  </DicomAttribute>
                  <DicomAttribute keyword="MeasuredValueSequence" tag="0040A300" vr="SQ">
                    <Item number="1">
                      <DicomAttribute keyword="MeasurementUnitsCodeSequence"
                        tag="004008EA" vr="SQ">
                        <Item number="1">
                          <DicomAttribute keyword="CodeValue" tag="00080100"
                            vr="SH">
                            <Value number="1">{X-Ray sources}</Value>
                          </DicomAttribute>
                          <DicomAttribute keyword="CodingSchemeDesignator"
                            tag="00080102" vr="SH">
                            <Value number="1">UCUM</Value>
                          </DicomAttribute>
                          <DicomAttribute keyword="CodeMeaning" tag="00080104"
                            vr="LO">
                            <Value number="1">X-Ray sources</Value>
                          </DicomAttribute>
                        </Item>
                      </DicomAttribute>
                      <DicomAttribute keyword="NumericValue" tag="0040A30A" vr="DS">
                        <!-- Edit -->
                        <Value number="1">0</Value>
                      </DicomAttribute>
                    </Item>
                  </DicomAttribute>
                </Item>
                <xsl:apply-templates select="/NativeDicomModel/DicomAttribute[@tag='0040030E']/Item"/>
              </DicomAttribute>
            </Item>
            <Item number="7">
              <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
                <Value number="1">CONTAINS</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
                <Value number="1">CONTAINER</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">113829</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">DCM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">CT Dose</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              <DicomAttribute keyword="ContinuityOfContent" tag="0040A050" vr="CS">
                <Value number="1">SEPARATE</Value>
              </DicomAttribute>
              <DicomAttribute keyword="ContentSequence" tag="0040A730" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
                    <Value number="1">CONTAINS</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
                    <Value number="1">NUM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
                    <Item number="1">
                      <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                        <Value number="1">113830</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102"
                        vr="SH">
                        <Value number="1">DCM</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                        <Value number="1">Mean CTDIvol</Value>
                      </DicomAttribute>
                    </Item>
                  </DicomAttribute>
                  <DicomAttribute keyword="MeasuredValueSequence" tag="0040A300" vr="SQ">
                    <Item number="1">
                      <DicomAttribute keyword="MeasurementUnitsCodeSequence" tag="004008EA" vr="SQ">
                        <Item number="1">
                          <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                            <Value number="1">mGy</Value>
                          </DicomAttribute>
                          <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                            <Value number="1">UCUM</Value>
                          </DicomAttribute>
                          <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                            <Value number="1">mGy</Value>
                          </DicomAttribute>
                        </Item>
                      </DicomAttribute>
                      <DicomAttribute keyword="NumericValue" tag="0040A30A" vr="DS">
                        <Value number="1"><xsl:value-of select="sum(/NativeDicomModel/DicomAttribute[@tag='0040030E']/child::*/DicomAttribute[@tag='00189345']/Value) div count(/NativeDicomModel/DicomAttribute[@tag='0040030E']/Item)"/></Value>
                      </DicomAttribute>
                    </Item>
                  </DicomAttribute>
                </Item>
                <Item number="2">
                  <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
                    <Value number="1">CONTAINS</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
                    <Value number="1">CODE</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
                    <Item number="1">
                      <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                        <Value number="1">113835</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                        <Value number="1">DCM</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                        <Value number="1">CTDIw Phantom Type</Value>
                      </DicomAttribute>
                    </Item>
                  </DicomAttribute>
                  <DicomAttribute keyword="ConceptCodeSequence" tag="0040A168" vr="SQ">
                    <!-- cf. DICOM PS 3.16 2011 CID 4052 for other CTDIw Phantom Types -->
                    <Item number="1">
                      <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                        <Value number="1">113681</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                        <Value number="1">DCM</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                        <Value number="1">Phantom</Value>
                      </DicomAttribute>
                    </Item>
                  </DicomAttribute>
                </Item>
                <Item number="3">
                  <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
                    <Value number="1">CONTAINS</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
                    <Value number="1">NUM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
                    <Item number="1">
                      <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                        <Value number="1">113838</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                        <Value number="1">DCM</Value>
                      </DicomAttribute>
                      <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                        <Value number="1">DLP</Value>
                      </DicomAttribute>
                    </Item>
                  </DicomAttribute>
                  <DicomAttribute keyword="MeasuredValueSequence" tag="0040A300" vr="SQ">
                    <Item number="1">
                      <DicomAttribute keyword="MeasurementUnitsCodeSequence" tag="004008EA" vr="SQ">
                        <Item number="1">
                          <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                            <Value number="1">mGy.cm</Value>
                          </DicomAttribute>
                          <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                            <Value number="1">UCUM</Value>
                          </DicomAttribute>
                          <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                            <Value number="1">mGy.cm</Value>
                          </DicomAttribute>
                        </Item>
                      </DicomAttribute>
                      <DicomAttribute keyword="NumericValue" tag="0040A30A" vr="DS">
                        <Value number="1">
                          <xsl:choose>
                            <xsl:when test="/NativeDicomModel/DicomAttribute[@tag='0018115E']/Value">
                              <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='0018115E']/Value div 100000"/>
                            </xsl:when>
                            <xsl:when test="/NativeDicomModel/DicomAttribute[@tag='00400302']/Value">
                              <xsl:value-of select="/NativeDicomModel/DicomAttribute[@tag='00400302']/Value * /NativeDicomModel/DicomAttribute[@tag='00400303']/Value"/>
                            </xsl:when>
                            <xsl:otherwise>
                              <!-- This fallback method needs to be adjusted to the format of 'CommentsOnRadiationDose' of the particular CT -->
                              <xsl:value-of select="substring-after(/NativeDicomModel/DicomAttribute[@tag='00400310']/Value[1], 'Total DLP=')"/>
                            </xsl:otherwise>
                          </xsl:choose>
                        </Value>
                      </DicomAttribute>
                    </Item>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
        </Item>
        <Item number="8">
          <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
            <Value number="1">CONTAINS</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
            <Value number="1">CODE</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                <Value number="1">113854</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                <Value number="1">DCM</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                <Value number="1">Source of Dose Information</Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
          <DicomAttribute keyword="ConceptCodeSequence" tag="0040A168" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                <Value number="1">113856</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                <Value number="1">DCM</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                <Value number="1">Automated Data Collection</Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
        </Item>
      </DicomAttribute>
    </NativeDicomModel>
  </xsl:template>
  
  <xsl:template  name="XRaySourceParameters" match="Item">
    <Item number="{@number + 5}">
      <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
        <Value number="1">CONTAINS</Value>
      </DicomAttribute>
      <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
        <Value number="1">CONTAINER</Value>
      </DicomAttribute>
      <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
        <Item number="1">
          <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
            <Value number="1">113831</Value>
          </DicomAttribute>
          <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
            <Value number="1">DCM</Value>
          </DicomAttribute>
          <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
            <Value number="1">CT X-Ray Source Parameters</Value>
          </DicomAttribute>
        </Item>
      </DicomAttribute>
      <DicomAttribute keyword="ContinuityOfContent" tag="0040A050" vr="CS">
        <Value number="1">SEPARATE</Value>
      </DicomAttribute>
      <DicomAttribute keyword="ContentSequence" tag="0040A730" vr="SQ">
        <Item number="1">
          <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
            <Value number="1">CONTAINS</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
            <Value number="1">TEXT</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                <Value number="1">113832</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                <Value number="1">DCM</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                <Value number="1">Identification Number of the X-Ray Source</Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
          <DicomAttribute keyword="TextValue" tag="0040A160" vr="UT">
            <!-- Edit -->
            <Value number="1">0</Value>
          </DicomAttribute>
        </Item>
        <Item number="2">
          <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
            <Value number="1">CONTAINS</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
            <Value number="1">NUM</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                <Value number="1">113733</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                <Value number="1">DCM</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                <Value number="1">KVP</Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
          <DicomAttribute keyword="MeasuredValueSequence" tag="0040A300" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="MeasurementUnitsCodeSequence" tag="004008EA" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">kV</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">UCUM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">kV</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              <DicomAttribute keyword="NumericValue" tag="0040A30A" vr="DS">
                <Value number="1"><xsl:value-of select="DicomAttribute[@tag='00180060']/Value"/></Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
        </Item>
        <Item number="3">
          <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
            <Value number="1">CONTAINS</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
            <Value number="1">NUM</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                <Value number="1">113833</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                <Value number="1">DCM</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                <Value number="1">Maximum X-Ray Tube Current</Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
          <DicomAttribute keyword="MeasuredValueSequence" tag="0040A300" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="MeasurementUnitsCodeSequence" tag="004008EA" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">mA</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">UCUM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">mA</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              <DicomAttribute keyword="NumericValue" tag="0040A30A" vr="DS">
                <Value number="1"><xsl:value-of select="DicomAttribute[@tag='00188151']/Value div 1000"/></Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
        </Item>
        <Item number="4">
          <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
            <Value number="1">CONTAINS</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
            <Value number="1">NUM</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                <Value number="1">113734</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                <Value number="1">DCM</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                <Value number="1">X-Ray Tube Current</Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
          <DicomAttribute keyword="MeasuredValueSequence" tag="0040A300" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="MeasurementUnitsCodeSequence" tag="004008EA" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">mA</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">UCUM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">mA</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              <DicomAttribute keyword="NumericValue" tag="0040A30A" vr="DS">
                <Value number="1"><xsl:value-of select="DicomAttribute[@tag='00188151']/Value div 1000"/></Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
        </Item>
        <Item number="5">
          <DicomAttribute keyword="RelationshipType" tag="0040A010" vr="CS">
            <Value number="1">CONTAINS</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ValueType" tag="0040A040" vr="CS">
            <Value number="1">NUM</Value>
          </DicomAttribute>
          <DicomAttribute keyword="ConceptNameCodeSequence" tag="0040A043" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                <Value number="1">113834</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                <Value number="1">DCM</Value>
              </DicomAttribute>
              <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                <Value number="1">Exposure Time per Rotation</Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
          <DicomAttribute keyword="MeasuredValueSequence" tag="0040A300" vr="SQ">
            <Item number="1">
              <DicomAttribute keyword="MeasurementUnitsCodeSequence" tag="004008EA" vr="SQ">
                <Item number="1">
                  <DicomAttribute keyword="CodeValue" tag="00080100" vr="SH">
                    <Value number="1">s</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodingSchemeDesignator" tag="00080102" vr="SH">
                    <Value number="1">UCUM</Value>
                  </DicomAttribute>
                  <DicomAttribute keyword="CodeMeaning" tag="00080104" vr="LO">
                    <Value number="1">s</Value>
                  </DicomAttribute>
                </Item>
              </DicomAttribute>
              <DicomAttribute keyword="NumericValue" tag="0040A30A" vr="DS">
                <Value number="1"><xsl:value-of select="DicomAttribute[@tag='00181152']/Value"/></Value>
              </DicomAttribute>
            </Item>
          </DicomAttribute>
        </Item>
      </DicomAttribute>
    </Item>
  </xsl:template>
  
</xsl:stylesheet>