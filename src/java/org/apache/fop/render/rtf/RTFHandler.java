/*
 * Copyright 1999-2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id$ */

package org.apache.fop.render.rtf;

// Java
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Iterator;

import org.apache.commons.logging.impl.SimpleLog;
import org.apache.commons.logging.Log;
import org.apache.fop.apps.FOPException;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.fo.FOEventHandler;
import org.apache.fop.fo.FONode;
import org.apache.fop.fo.flow.BasicLink;
import org.apache.fop.fo.flow.Block;
import org.apache.fop.fo.flow.BlockContainer;
import org.apache.fop.fo.flow.Character;
import org.apache.fop.fo.flow.ExternalGraphic;
import org.apache.fop.fo.flow.Footnote;
import org.apache.fop.fo.flow.FootnoteBody;
import org.apache.fop.fo.flow.Inline;
import org.apache.fop.fo.flow.InstreamForeignObject;
import org.apache.fop.fo.flow.Leader;
import org.apache.fop.fo.flow.ListBlock;
import org.apache.fop.fo.flow.ListItem;
import org.apache.fop.fo.flow.ListItemLabel;
import org.apache.fop.fo.flow.PageNumber;
import org.apache.fop.fo.flow.Table;
import org.apache.fop.fo.flow.TableColumn;
import org.apache.fop.fo.flow.TableBody;
import org.apache.fop.fo.flow.TableCell;
import org.apache.fop.fo.flow.TableHeader;
import org.apache.fop.fo.flow.TableRow;
import org.apache.fop.fo.pagination.Flow;
import org.apache.fop.fo.pagination.PageSequence;
import org.apache.fop.fo.pagination.SimplePageMaster;
import org.apache.fop.fo.properties.EnumProperty;
import org.apache.fop.fo.properties.FixedLength;
import org.apache.fop.fo.properties.LengthProperty;
import org.apache.fop.fo.properties.Property;
import org.apache.fop.fo.properties.StringProperty;
import org.apache.fop.fo.Constants;
import org.apache.fop.fo.FOText;
import org.apache.fop.render.rtf.rtflib.rtfdoc.ITableAttributes;
import org.apache.fop.render.rtf.rtflib.rtfdoc.IRtfAfterContainer;
import org.apache.fop.render.rtf.rtflib.rtfdoc.IRtfBeforeContainer;
import org.apache.fop.render.rtf.rtflib.rtfdoc.IRtfListContainer;
import org.apache.fop.render.rtf.rtflib.rtfdoc.IRtfTextrunContainer;
import org.apache.fop.render.rtf.rtflib.rtfdoc.RtfAfter;
import org.apache.fop.render.rtf.rtflib.rtfdoc.RtfAttributes;
import org.apache.fop.render.rtf.rtflib.rtfdoc.RtfBefore;
import org.apache.fop.render.rtf.rtflib.rtfdoc.RtfDocumentArea;
import org.apache.fop.render.rtf.rtflib.rtfdoc.RtfElement;
import org.apache.fop.render.rtf.rtflib.rtfdoc.RtfExternalGraphic;
import org.apache.fop.render.rtf.rtflib.rtfdoc.RtfFile;
import org.apache.fop.render.rtf.rtflib.rtfdoc.RtfFootnote;
import org.apache.fop.render.rtf.rtflib.rtfdoc.RtfHyperLink;
import org.apache.fop.render.rtf.rtflib.rtfdoc.RtfList;
import org.apache.fop.render.rtf.rtflib.rtfdoc.RtfListItem;
import org.apache.fop.render.rtf.rtflib.rtfdoc.RtfListItem.RtfListItemLabel;
import org.apache.fop.render.rtf.rtflib.rtfdoc.RtfSection;
import org.apache.fop.render.rtf.rtflib.rtfdoc.RtfTextrun;
import org.apache.fop.render.rtf.rtflib.rtfdoc.RtfTable;
import org.apache.fop.render.rtf.rtflib.rtfdoc.RtfTableRow;
import org.apache.fop.render.rtf.rtflib.rtfdoc.RtfTableCell;
import org.apache.fop.render.rtf.rtflib.rtfdoc.IRtfTableContainer;
import org.apache.fop.fonts.FontSetup;
import org.xml.sax.SAXException;

/**
 * RTF Handler: generates RTF output using the structure events from
 * the FO Tree sent to this structure handler.
 *
 * @author Bertrand Delacretaz <bdelacretaz@codeconsult.ch>
 * @author Trembicki-Guy, Ed <GuyE@DNB.com>
 * @author Boris Poudérous <boris.pouderous@eads-telecom.com>
 * @author Peter Herweg <pherweg@web.de>
 * @author Andreas Putz <a.putz@skynamics.com>
 */
public class RTFHandler extends FOEventHandler {

    private RtfFile rtfFile;
    private final OutputStream os;
    private final Log log = new SimpleLog("FOP/RTF");
    private RtfSection sect;
    private RtfDocumentArea docArea;
    private int iNestCount;
    private boolean bDefer;              //true, if each called handler shall be
                                         //processed at later time.
    private boolean bDeferredExecution;  //true, if currently called handler was not
                                         //called while SAX parsing, but was called
                                         //by invokeDeferredEvent.
    private boolean bPrevHeaderSpecified = false;//true, if there has been a
                                                 //header in any page-sequence
    private boolean bPrevFooterSpecified = false;//true, if there has been a
                                                 //footer in any page-sequence
    private boolean bHeaderSpecified = false;  //true, if there is a header
                                               //in current page-sequence
    private boolean bFooterSpecified = false;  //true, if there is a footer
                                               //in current page-sequence
    private BuilderContext builderContext = new BuilderContext(null);

    private static final String ALPHA_WARNING = "WARNING: RTF renderer is "
        + "veryveryalpha at this time, see class org.apache.fop.rtf.renderer.RTFHandler";

    /**
     * Creates a new RTF structure handler.
     * @param userAgent the FOUserAgent for this process
     * @param os OutputStream to write to
     */
    public RTFHandler(FOUserAgent userAgent, OutputStream os) {
        super(userAgent);
        this.os = os;
        bDefer = false;
        bDeferredExecution = false;
        iNestCount=0;
        FontSetup.setup(fontInfo, null);
        log.warn(ALPHA_WARNING);
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#startDocument()
     */
    public void startDocument() throws SAXException {
        // TODO sections should be created
        try {
            rtfFile = new RtfFile(new OutputStreamWriter(os));
            docArea = rtfFile.startDocumentArea();
        } catch (IOException ioe) {
            // TODO could we throw Exception in all FOEventHandler events?
            throw new SAXException(ioe);
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#endDocument()
     */
    public void endDocument() throws SAXException {
        try {
            rtfFile.flush();
        } catch (IOException ioe) {
            // TODO could we throw Exception in all FOEventHandler events?
            throw new SAXException(ioe);
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler
     */
    public void startPageSequence(PageSequence pageSeq)  {
        try {
            if (bDefer) {
                return;
            }

            sect = docArea.newSection();

            //read page size and margins, if specified
            Property prop;
            if ((prop = pageSeq.getProperty(Constants.PR_MASTER_REFERENCE)) != null) {
                String reference = prop.getString();

                SimplePageMaster pagemaster 
                    = pageSeq.getRoot().getLayoutMasterSet().getSimplePageMaster(reference);

                //only simple-page-master supported, so pagemaster may be null
                if (pagemaster != null) {
                    sect.getRtfAttributes().set(
                        PageAttributesConverter.convertPageAttributes(
                                pagemaster));
                }
            }

            builderContext.pushContainer(sect);

            bHeaderSpecified = false;
            bFooterSpecified = false;
        } catch (IOException ioe) {
            // TODO could we throw Exception in all FOEventHandler events?
            log.error("startPageSequence: " + ioe.getMessage());
            //TODO throw new FOPException(ioe);
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#endPageSequence(PageSequence)
     */
    public void endPageSequence(PageSequence pageSeq) {
        if (bDefer) {
            return;
        }

        builderContext.popContainer();
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#startFlow(Flow)
     */
    public void startFlow(Flow fl) {
        if (bDefer) {
            return;
        }

        try {
            if (fl.getPropString(Constants.PR_FLOW_NAME).equals("xsl-region-body")) {
                // if there is no header in current page-sequence but there has been
                // a header in a previous page-sequence, insert an empty header.
                if (bPrevHeaderSpecified && !bHeaderSpecified) {
                    RtfAttributes attr = new RtfAttributes();
                    attr.set(RtfBefore.HEADER);

                    final IRtfBeforeContainer contBefore 
                        = (IRtfBeforeContainer)builderContext.getContainer
                                (IRtfBeforeContainer.class, true, this);
                    contBefore.newBefore(attr);
                }

                // if there is no footer in current page-sequence but there has been
                // a footer in a previous page-sequence, insert an empty footer.
                if (bPrevFooterSpecified && !bFooterSpecified) {
                    RtfAttributes attr = new RtfAttributes();
                    attr.set(RtfAfter.FOOTER);

                    final IRtfAfterContainer contAfter
                        = (IRtfAfterContainer)builderContext.getContainer
                                (IRtfAfterContainer.class, true, this);
                    contAfter.newAfter(attr);
                }

            } else if (fl.getPropString(Constants.PR_FLOW_NAME).equals("xsl-region-before")) {
                bHeaderSpecified = true;
                bPrevHeaderSpecified = true;

                final IRtfBeforeContainer c 
                    = (IRtfBeforeContainer)builderContext.getContainer(
                        IRtfBeforeContainer.class,
                        true, this);

                RtfAttributes beforeAttributes = ((RtfElement)c).getRtfAttributes();
                if (beforeAttributes == null) {
                    beforeAttributes = new RtfAttributes();
                }
                beforeAttributes.set(RtfBefore.HEADER);

                RtfBefore before = c.newBefore(beforeAttributes);
                builderContext.pushContainer(before);
            } else if (fl.getPropString(Constants.PR_FLOW_NAME).equals("xsl-region-after")) {
                bFooterSpecified = true;
                bPrevFooterSpecified = true;

                final IRtfAfterContainer c 
                    = (IRtfAfterContainer)builderContext.getContainer(
                        IRtfAfterContainer.class,
                        true, this);

                RtfAttributes afterAttributes = ((RtfElement)c).getRtfAttributes();
                if (afterAttributes == null) {
                    afterAttributes = new RtfAttributes();
                }

                afterAttributes.set(RtfAfter.FOOTER);

                RtfAfter after = c.newAfter(afterAttributes);
                builderContext.pushContainer(after);
            }
        } catch (IOException ioe) {
            log.error("startFlow: " + ioe.getMessage());
            throw new RuntimeException(ioe.getMessage());
        } catch (Exception e) {
            log.error("startFlow: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#endFlow(Flow)
     */
    public void endFlow(Flow fl) {
        if (bDefer) {
            return;
        }

        try {
            if (fl.getPropString(Constants.PR_FLOW_NAME).equals("xsl-region-body")) {
                //just do nothing
            } else if (fl.getPropString(Constants.PR_FLOW_NAME).equals("xsl-region-before")) {
                builderContext.popContainer();
            } else if (fl.getPropString(Constants.PR_FLOW_NAME).equals("xsl-region-after")) {
                builderContext.popContainer();
            }
        } catch (Exception e) {
            log.error("endFlow: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#startBlock(Block)
     */
    public void startBlock(Block bl) {
        ++iNestCount;
        
        if (!bDeferredExecution) {
            //If startBlock was called while SAX parsing, defer processing of this
            //FO and all its elements until endBlock. This has to be done, because
            //attributes (for example while-space-treatment, linefeed-treatment)
            //are not available until endBlock.
            bDefer = true;
        }
        if (bDefer) {
            return;
        }
       
        try {
            RtfAttributes rtfAttr
                = TextAttributesConverter.convertAttributes(bl);
                    
            IRtfTextrunContainer container 
                = (IRtfTextrunContainer)builderContext.getContainer(
                    IRtfTextrunContainer.class,
                    true, this);

            RtfTextrun textrun = container.getTextrun();
            
            textrun.addParagraphBreak();
            textrun.pushAttributes(rtfAttr);
        } catch (IOException ioe) {
            // TODO could we throw Exception in all FOEventHandler events?
            log.error("startBlock: " + ioe.getMessage());
            throw new RuntimeException("IOException: " + ioe);
        } catch (Exception e) {
            log.error("startBlock: " + e.getMessage());
            throw new RuntimeException("Exception: " + e);
        }
    }


    /**
     * @see org.apache.fop.fo.FOEventHandler#endBlock(Block)
     */
    public void endBlock(Block bl) {
        --iNestCount;
        
        if (!bDeferredExecution && iNestCount==0) {
            //If endBlock was called while SAX parsing, and the passed FO is Block
            //nested within another Block, stop deferring.
            //Now process all deferred FOs.
            bDefer = false;
            
            bDeferredExecution=true;
            recurseFONode(bl);
            bDeferredExecution=false;
            
            //exit function, because the code has already beed executed while 
            //deferred execution.   
            return;
        }
        
        if(bDefer) {
            return;
        }
        
        try {
            IRtfTextrunContainer container 
                = (IRtfTextrunContainer)builderContext.getContainer(
                    IRtfTextrunContainer.class,
                    true, this);
                    
            RtfTextrun textrun = container.getTextrun();
            
            textrun.addParagraphBreak();
            textrun.popAttributes();
            
        } catch (IOException ioe) {
            log.error("startBlock:" + ioe.getMessage());
            throw new RuntimeException(ioe.getMessage());
        } catch (Exception e) {
            log.error("startBlock:" + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#startBlockContainer(BlockContainer)
     */
    public void startBlockContainer(BlockContainer blc) {
        if (bDefer) {
            return;
        }
       
        try {
            RtfAttributes rtfAttr
                = TextAttributesConverter.convertBlockContainerAttributes(blc);
                    
            IRtfTextrunContainer container 
                = (IRtfTextrunContainer)builderContext.getContainer(
                    IRtfTextrunContainer.class,
                    true, this);

            RtfTextrun textrun = container.getTextrun();
            
            textrun.addParagraphBreak();
            textrun.pushAttributes(rtfAttr);
        } catch (IOException ioe) {
            // TODO could we throw Exception in all FOEventHandler events?
            log.error("startBlock: " + ioe.getMessage());
            throw new RuntimeException("IOException: " + ioe);
        } catch (Exception e) {
            log.error("startBlock: " + e.getMessage());
            throw new RuntimeException("Exception: " + e);
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#endBlockContainer(BlockContainer)
     */
    public void endBlockContainer(BlockContainer bl) {
        if(bDefer) {
            return;
        }
        
        try {
            IRtfTextrunContainer container 
                = (IRtfTextrunContainer)builderContext.getContainer(
                    IRtfTextrunContainer.class,
                    true, this);
                    
            RtfTextrun textrun = container.getTextrun();
            
            textrun.addParagraphBreak();
            textrun.popAttributes();
            
        } catch (IOException ioe) {
            log.error("startBlock:" + ioe.getMessage());
            throw new RuntimeException(ioe.getMessage());
        } catch (Exception e) {
            log.error("startBlock:" + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#startTable(Table)
     */
    public void startTable(Table tbl) {
        if (bDefer) {
            return;
        }

        // create an RtfTable in the current table container
        TableContext tableContext = new TableContext(builderContext);

        try {
            RtfAttributes atts 
                = TableAttributesConverter.convertTableAttributes(tbl);
            
            final IRtfTableContainer tc 
                = (IRtfTableContainer)builderContext.getContainer(
                    IRtfTableContainer.class, true, null);
            builderContext.pushContainer(tc.newTable(atts, tableContext));
        } catch (Exception e) {
            log.error("startTable:" + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }

        builderContext.pushTableContext(tableContext);
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#endTable(Table)
     */
    public void endTable(Table tbl) {
        if (bDefer) {
            return;
        }

        builderContext.popTableContext();
        builderContext.popContainer();
    }

    /**
    *
    * @param tc TableColumn that is starting;
    */

    public void startColumn(TableColumn tc) {
        if (bDefer) {
            return;
        }

        try {
            Integer iWidth = new Integer(tc.getColumnWidth().getValue() / 1000);
            builderContext.getTableContext().setNextColumnWidth(iWidth.toString() + "pt");
            builderContext.getTableContext().setNextColumnRowSpanning(new Integer(0), null);
        } catch (Exception e) {
            log.error("startColumn: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }

    }

     /**
     *
     * @param tc TableColumn that is ending;
     */

    public void endColumn(TableColumn tc) {
        if (bDefer) {
            return;
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#startHeader(TableBody)
     */
    public void startHeader(TableBody th) {
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#endHeader(TableBody)
     */
    public void endHeader(TableBody th) {
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#startFooter(TableBody)
     */
    public void startFooter(TableBody tf) {
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#endFooter(TableBody)
     */
    public void endFooter(TableBody tf) {
    }

    /**
     *
     * @param inl Inline that is starting.
     */
    public void startInline(Inline inl) {
        if (bDefer) {
            return;
        }

        try {
            RtfAttributes rtfAttr
                = TextAttributesConverter.convertCharacterAttributes(inl);
                    
            IRtfTextrunContainer container
                = (IRtfTextrunContainer)builderContext.getContainer(
                    IRtfTextrunContainer.class, true, this);
                    
            RtfTextrun textrun = container.getTextrun();
            textrun.pushAttributes(rtfAttr);
        } catch (IOException ioe) {
            log.error("startInline:" + ioe.getMessage());
            throw new RuntimeException(ioe.getMessage());
        } catch (FOPException fe) {
            log.error("startInline:" + fe.getMessage());
            throw new RuntimeException(fe.getMessage());
        } catch (Exception e) {
            log.error("startInline:" + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     *
     * @param inl Inline that is ending.
     */
    public void endInline(Inline inl) {
        if (bDefer) {
            return;
        }

        try {
            IRtfTextrunContainer container
                = (IRtfTextrunContainer)builderContext.getContainer(
                    IRtfTextrunContainer.class, true, this);
                    
            RtfTextrun textrun = container.getTextrun();
            textrun.popAttributes();
        } catch (IOException ioe) {
            log.error("startInline:" + ioe.getMessage());
            throw new RuntimeException(ioe.getMessage());
        } catch (Exception e) {
            log.error("startInline:" + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

     /**
     * @see org.apache.fop.fo.FOEventHandler#startBody(TableBody)
     */
    public void startBody(TableBody tb) {
        if (bDefer) {
            return;
        }

        try {
            RtfAttributes atts = TableAttributesConverter.convertRowAttributes (tb,
                   null);

            RtfTable tbl = (RtfTable)builderContext.getContainer(RtfTable.class, true, this);
            tbl.setHeaderAttribs(atts);
        } catch (Exception e) {
            log.error("startBody: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#endBody(TableBody)
     */
    public void endBody(TableBody tb) {
        if (bDefer) {
            return;
        }

        try {
            RtfTable tbl = (RtfTable)builderContext.getContainer(RtfTable.class, true, this);
            tbl.setHeaderAttribs(null);
        } catch (Exception e) {
            log.error("endBody: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#startRow(TableRow)
     */
    public void startRow(TableRow tr) {
        if (bDefer) {
            return;
        }

        try {
            // create an RtfTableRow in the current RtfTable
            final RtfTable tbl = (RtfTable)builderContext.getContainer(RtfTable.class,
                    true, null);

            RtfAttributes atts = TableAttributesConverter.convertRowAttributes(tr,
                    tbl.getHeaderAttribs());
                    
            if (tr.getParent() instanceof TableHeader) {
                atts.set(ITableAttributes.ATTR_HEADER);
            }

            builderContext.pushContainer(tbl.newTableRow(atts));

            // reset column iteration index to correctly access column widths
            builderContext.getTableContext().selectFirstColumn();
        } catch (Exception e) {
            log.error("startRow: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#endRow(TableRow)
     */
    public void endRow(TableRow tr) {
        if (bDefer) {
            return;
        }

        builderContext.popContainer();
        builderContext.getTableContext().decreaseRowSpannings();
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#startCell(TableCell)
     */
    public void startCell(TableCell tc) {
        if (bDefer) {
            return;
        }

        try {
            TableContext tctx = builderContext.getTableContext();
            final RtfTableRow row = (RtfTableRow)builderContext.getContainer(RtfTableRow.class,
                    true, null);


            //while the current column is in row-spanning, act as if
            //a vertical merged cell would have been specified.
            while (tctx.getNumberOfColumns() > tctx.getColumnIndex()
                  && tctx.getColumnRowSpanningNumber().intValue() > 0) {
                row.newTableCellMergedVertically((int)tctx.getColumnWidth(),
                        tctx.getColumnRowSpanningAttrs());
                tctx.selectNextColumn();
            }

            //get the width of the currently started cell
            float width = tctx.getColumnWidth();

            // create an RtfTableCell in the current RtfTableRow
            RtfAttributes atts = TableAttributesConverter.convertCellAttributes(tc);
            RtfTableCell cell = row.newTableCell((int)width, atts);

            //process number-rows-spanned attribute
            Property p = null;
            if ((p = tc.getProperty(Constants.PR_NUMBER_ROWS_SPANNED)) != null) {
                // Start vertical merge
                cell.setVMerge(RtfTableCell.MERGE_START);

                // set the number of rows spanned
                tctx.setCurrentColumnRowSpanning(new Integer(p.getNumber().intValue()),
                        cell.getRtfAttributes());
            } else {
                tctx.setCurrentColumnRowSpanning(new Integer(1), null);
            }

            builderContext.pushContainer(cell);
        } catch (Exception e) {
            log.error("startCell: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#endCell(TableCell)
     */
    public void endCell(TableCell tc) {
        if (bDefer) {
            return;
        }

        builderContext.popContainer();
        builderContext.getTableContext().selectNextColumn();
    }

    // Lists
    /**
     * @see org.apache.fop.fo.FOEventHandler#startList(ListBlock)
     */
    public void startList(ListBlock lb) {
        if (bDefer) {
            return;
        }

        try  {
            // create an RtfList in the current list container
            final IRtfListContainer c
                = (IRtfListContainer)builderContext.getContainer(
                    IRtfListContainer.class, true, this);
            final RtfList newList = c.newList(
                ListAttributesConverter.convertAttributes(lb));
            builderContext.pushContainer(newList);
        } catch (IOException ioe) {
            log.error("startList: " + ioe.getMessage());
            throw new RuntimeException(ioe.getMessage());
        } catch (FOPException fe) {
            log.error("startList: " + fe.getMessage());
            throw new RuntimeException(fe.getMessage());
        } catch (Exception e) {
            log.error("startList: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#endList(ListBlock)
     */
    public void endList(ListBlock lb) {
        if (bDefer) {
            return;
        }

        builderContext.popContainer();
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#startListItem(ListItem)
     */
    public void startListItem(ListItem li) {
        if (bDefer) {
            return;
        }

        // create an RtfListItem in the current RtfList
        try {
            final RtfList list = (RtfList)builderContext.getContainer(
                    RtfList.class, true, this);
            builderContext.pushContainer(list.newListItem());
        } catch (IOException ioe) {
            log.error("startList: " + ioe.getMessage());
            throw new RuntimeException(ioe.getMessage());
        } catch (FOPException fe) {
            log.error("startList: " + fe.getMessage());
            throw new RuntimeException(fe.getMessage());
        } catch (Exception e) {
            log.error("startList: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#endListItem(ListItem)
     */
    public void endListItem(ListItem li) {
        if (bDefer) {
            return;
        }

        builderContext.popContainer();
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#startListLabel()
     */
    public void startListLabel() {
        if (bDefer) {
            return;
        }

        try {
            RtfListItem item
                = (RtfListItem)builderContext.getContainer(RtfListItem.class, true, this);
                
            RtfListItemLabel label = item.new RtfListItemLabel(item);
            builderContext.pushContainer(label);
        } catch (IOException ioe) {
            log.error("startPageNumber:" + ioe.getMessage());
            throw new RuntimeException(ioe.getMessage());
        } catch (Exception e) {
            log.error("startPageNumber: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#endListLabel()
     */
    public void endListLabel() {
        if (bDefer) {
            return;
        }

        builderContext.popContainer();
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#startListBody()
     */
    public void startListBody() {
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#endListBody()
     */
    public void endListBody() {
    }

    // Static Regions
    /**
     * @see org.apache.fop.fo.FOEventHandler#startStatic()
     */
    public void startStatic() {
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#endStatic()
     */
    public void endStatic() {
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#startMarkup()
     */
    public void startMarkup() {
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#endMarkup()
     */
    public void endMarkup() {
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#startLink(BasicLink basicLink)
     */
    public void startLink(BasicLink basicLink) {
        if (bDefer) {
            return;
        }

        try {
            IRtfTextrunContainer container
                = (IRtfTextrunContainer)builderContext.getContainer(
                    IRtfTextrunContainer.class, true, this);
            
            RtfTextrun textrun=container.getTextrun();
            
            RtfHyperLink link=textrun.addHyperlink(new RtfAttributes());
            
            StringProperty internal
                = (StringProperty)basicLink.getProperty(Constants.PR_INTERNAL_DESTINATION);
            StringProperty external
                = (StringProperty)basicLink.getProperty(Constants.PR_EXTERNAL_DESTINATION);
            
            if(external != null) {
                link.setExternalURL(external.getString());
            } else if(internal != null) {
                link.setInternalURL(internal.getString());
            }
            
            builderContext.pushContainer(link);
            
        } catch (IOException ioe) {
            log.error("startLink:" + ioe.getMessage());
            throw new RuntimeException(ioe.getMessage());
        } catch (Exception e) {
            log.error("startLink: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#endLink()
     */
    public void endLink() {
        if (bDefer) {
            return;
        }

        builderContext.popContainer();
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#image(ExternalGraphic)
     */
    public void image(ExternalGraphic eg) {
        if (bDefer) {
            return;
        }

        try {
       
        
            final IRtfTextrunContainer c 
                = (IRtfTextrunContainer)builderContext.getContainer(
                    IRtfTextrunContainer.class, true, this);
            
            final RtfExternalGraphic newGraphic = c.getTextrun().newImage();
       
            Property p = null; 
               
            //get source file
            if ((p = eg.getProperty(Constants.PR_SRC)) != null) {
                newGraphic.setURL (p.getString());
            } else {
                log.error("The attribute 'src' of <fo:external-graphic> is required.");
                return;
            }
            
            //get scaling
            if ((p = eg.getProperty(Constants.PR_SCALING)) != null) {
                EnumProperty e = (EnumProperty)p;
                if (p.getEnum() == Constants.UNIFORM) {
                    newGraphic.setScaling ("uniform");
                }
            }
            
            //get width
            if ((p = eg.getProperty(Constants.PR_WIDTH)) != null) {
                LengthProperty lengthProp = (LengthProperty)p;
                if (lengthProp.getLength() instanceof FixedLength) {
                    Float f = new Float(lengthProp.getLength().getValue() / 1000f);
                    String sValue = f.toString() + "pt";
                    newGraphic.setWidth(sValue);
                }
            }
            
            //get height
            if ((p = eg.getProperty(Constants.PR_HEIGHT)) != null) {
                LengthProperty lengthProp = (LengthProperty)p;
                if (lengthProp.getLength() instanceof FixedLength) {
                    Float f = new Float(lengthProp.getLength().getValue() / 1000f);
                    String sValue = f.toString() + "pt";
                    newGraphic.setHeight(sValue);
                }
            }

            //TODO: make this configurable:
            //      int compression = m_context.m_options.getRtfExternalGraphicCompressionRate ();
            int compression = 0;
            if (compression != 0) {
                if (!newGraphic.setCompressionRate(compression)) {
                    log.warn("The compression rate " + compression 
                        + " is invalid. The value has to be between 1 and 100 %.");
                }
            }
        } catch (Exception e) {
            log.error("image: " + e.getMessage());
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#pageRef()
     */
    public void pageRef() {
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#foreignObject(InstreamForeignObject)
     */
    public void foreignObject(InstreamForeignObject ifo) {
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#startFootnote(Footnote)
     */
    public void startFootnote(Footnote footnote) {
        if (bDefer) {
            return;
        }

        try {
            RtfAttributes rtfAttr
                = TextAttributesConverter.convertAttributes(footnote);
                    
            IRtfTextrunContainer container 
                = (IRtfTextrunContainer)builderContext.getContainer(
                    IRtfTextrunContainer.class,
                    true, this);

            RtfTextrun textrun = container.getTextrun();
            RtfFootnote rtfFootnote = textrun.addFootnote();
            
            builderContext.pushContainer(rtfFootnote);
            
        } catch (IOException ioe) {
            // TODO could we throw Exception in all FOEventHandler events?
            log.error("startFootnote: " + ioe.getMessage());
            throw new RuntimeException("IOException: " + ioe);
        } catch (Exception e) {
            log.error("startFootnote: " + e.getMessage());
            throw new RuntimeException("Exception: " + e);
        }
    }
    
    /**
     * @see org.apache.fop.fo.FOEventHandler#endFootnote(Footnote)
     */
    public void endFootnote(Footnote footnote) {
        if (bDefer) {
            return;
        }

        builderContext.popContainer();
    }
    
    /**
     * @see org.apache.fop.fo.FOEventHandler#startFootnoteBody(FootnoteBody)
     */
    public void startFootnoteBody(FootnoteBody body) {
        if (bDefer) {
            return;
        }

        try {
            RtfFootnote rtfFootnote
                = (RtfFootnote)builderContext.getContainer(
                    RtfFootnote.class,
                    true, this);

            rtfFootnote.startBody();
        } catch (IOException ioe) {
            // TODO could we throw Exception in all FOEventHandler events?
            log.error("startFootnoteBody: " + ioe.getMessage());
            throw new RuntimeException("IOException: " + ioe);
        } catch (Exception e) {
            log.error("startFootnoteBody: " + e.getMessage());
            throw new RuntimeException("Exception: " + e);
        }
    }
    
    /**
     * @see org.apache.fop.fo.FOEventHandler#endFootnoteBody(FootnoteBody)
     */
    public void endFootnoteBody(FootnoteBody body) {
        if (bDefer) {
            return;
        }

        try {
            RtfFootnote rtfFootnote
                = (RtfFootnote)builderContext.getContainer(
                    RtfFootnote.class,
                    true, this);

            rtfFootnote.endBody();
        } catch (IOException ioe) {
            // TODO could we throw Exception in all FOEventHandler events?
            log.error("endFootnoteBody: " + ioe.getMessage());
            throw new RuntimeException("IOException: " + ioe);
        } catch (Exception e) {
            log.error("endFootnoteBody: " + e.getMessage());
            throw new RuntimeException("Exception: " + e);
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#leader(Leader)
     */
    public void leader(Leader l) {
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#character(Character)
     */
    public void character(Character c) {
        if (bDefer) {
            return;
        }
        
        try {
            RtfAttributes rtfAttr= 
                    TextAttributesConverter.convertCharacterAttributes(c);

            IRtfTextrunContainer container
                = (IRtfTextrunContainer)builderContext.getContainer(
                    IRtfTextrunContainer.class, true, this);
                    
            RtfTextrun textrun = container.getTextrun();
            
            textrun.pushAttributes(rtfAttr);            
            textrun.addString(c.getPropString(Constants.PR_CHARACTER));
            textrun.popAttributes();
         } catch (IOException ioe) {
            // FIXME could we throw Exception in all FOEventHandler events?
            log.error("character: " + ioe.getMessage());
            throw new RuntimeException(ioe.getMessage());
        } catch (Exception e) {
            log.error("character:" + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * @see org.apache.fop.fo.FOEventHandler#characters(char[], int, int)
     */
    public void characters(char[] data, int start, int length) {
        if (bDefer) {
            return;
        }
        
        try {
            IRtfTextrunContainer container
                = (IRtfTextrunContainer)builderContext.getContainer(
                    IRtfTextrunContainer.class, true, this);
                    
            RtfTextrun textrun = container.getTextrun();
            textrun.addString(new String(data, start, length-start));
         } catch (IOException ioe) {
            // FIXME could we throw Exception in all FOEventHandler events?
            log.error("characters: " + ioe.getMessage());
            throw new RuntimeException(ioe.getMessage());
        } catch (Exception e) {
            log.error("characters:" + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     *
     * @param pagenum PageNumber that is starting.
     */
    public void startPageNumber(PageNumber pagenum) {
        if (bDefer) {
            return;
        }

        try {
            RtfAttributes rtfAttr
                = TextAttributesConverter.convertCharacterAttributes(
                    pagenum);
                    
            IRtfTextrunContainer container
                = (IRtfTextrunContainer)builderContext.getContainer(
                    IRtfTextrunContainer.class, true, this);
                    
            RtfTextrun textrun = container.getTextrun();
            textrun.addPageNumber(rtfAttr);
        } catch (IOException ioe) {
            log.error("startPageNumber:" + ioe.getMessage());
            throw new RuntimeException(ioe.getMessage());
        } catch (Exception e) {
            log.error("startPageNumber: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     *
     * @param pagenum PageNumber that is ending.
     */
    public void endPageNumber(PageNumber pagenum) {
        if (bDefer) {
            return;
        }
    }
    
    /**
     * Calls the appropriate event handler for the passed FObj. 
     *
     * @param foNode FO node whose event is to be called
     * @param bStart TRUE calls the start handler, FALSE the end handler 
     */
    private void invokeDeferredEvent(FONode foNode, boolean bStart) {
        if (foNode instanceof Block) {
            if (bStart) {
                startBlock( (Block) foNode);
            } else {
                endBlock( (Block) foNode);
            }
        } else if (foNode instanceof Inline) {
            if (bStart) {
                startInline( (Inline) foNode);
            } else {
                endInline( (Inline) foNode);
            }
        } else if (foNode instanceof FOText) {
            if (bStart) {
                FOText text = (FOText) foNode;
                characters(text.ca, text.startIndex, text.endIndex);
            }
        } else if (foNode instanceof Character) {
            if (bStart) {
                Character c = (Character) foNode;
                character(c);
            }
        } else if (foNode instanceof BasicLink) {
            if (bStart) {
                startLink( (BasicLink) foNode);
            } else {
                endLink();
            }
        } else if (foNode instanceof PageNumber) {
            if (bStart) {
                startPageNumber( (PageNumber) foNode);
            } else {
                endPageNumber( (PageNumber) foNode);
            }
        } else if (foNode instanceof Footnote) {
            if (bStart) {
                startFootnote( (Footnote) foNode);
            } else {
                endFootnote( (Footnote) foNode);
            }
        } else if (foNode instanceof FootnoteBody) {
            if (bStart) {
                startFootnoteBody( (FootnoteBody) foNode);
            } else {
                endFootnoteBody( (FootnoteBody) foNode);
            }
        } else if (foNode instanceof ListBlock) {
            if (bStart) {
                startList( (ListBlock) foNode);
            } else {
                endList( (ListBlock) foNode);
            }
        } else if (foNode instanceof ListItem) {
            if (bStart) {
                startListItem( (ListItem) foNode);
            } else {
                endListItem( (ListItem) foNode);
            }
        } else if (foNode instanceof ListItemLabel) {
            if (bStart) {
                startListLabel();
            } else {
                endListLabel();
            }
        } else if (foNode instanceof Table) {
            if (bStart) {
                startTable( (Table) foNode);
            } else {
                endTable( (Table) foNode);
            }
        } else if (foNode instanceof TableColumn) {
            if (bStart) {
                startColumn( (TableColumn) foNode);
            } else {
                endColumn( (TableColumn) foNode);
            }
        } else if (foNode instanceof TableRow) {
            if (bStart) {
                startRow( (TableRow) foNode);
            } else {
                endRow( (TableRow) foNode);
            }
        } else if (foNode instanceof TableCell) {
            if (bStart) {
                startCell( (TableCell) foNode);
            } else {
                endCell( (TableCell) foNode);
            }
        }
    }
    
    /**
     * Calls the event handlers for the passed FONode and all its elements. 
     *
     * @param foNode FONode object which shall be recursed
     */
    private void recurseFONode(FONode foNode) {
        invokeDeferredEvent(foNode, true);
        
        if (foNode.getChildNodes() != null) {
            for(Iterator it = foNode.getChildNodes(); it.hasNext() ; ) {
                recurseFONode( (FONode) it.next() );
            }
        }
        
        invokeDeferredEvent(foNode, false);
    }
}
