package com.ansvia.hilda.ui

/**
 * Copyright (C) 2011-2012 Ansvia Inc.
 * User: robin
 * Date: 3/29/12
 * Time: 7:52 PM
 *
 */

import swing.event._
import swing._
import GridBagPanel._
import com.ansvia.hilda._
import javax.swing.event.TableModelListener
import javax.swing.table.{AbstractTableModel, TableModel}


class MainWindow extends SimpleSwingApplication {

    lazy val upd = new Updater()
    var modules = Array[IModuleUi]() 

    case class Logger(txt:TextArea) extends ILogger {
        def info(msg:String){ print("INFO: " + msg + "\n") }
        def error(msg:String){ print("ERROR: " + msg + "\n") }
        def debug(msg:String){ print("DEBUG: " + msg + "\n") }
        def warn(msg:String){ print("WARN: " + msg + "\n") }
        def print(msg:String){
            txt.append(msg)
        }
    }
    
    def loadModules(log0:ILogger) {
        modules = upd.getModules.map { m =>
            val mui = GitModule(m)
            mui.setLogger(log0)
            mui
        }
    }
    
    def getUiModule(name:String):IModuleUi = {
        for (mui <- modules){
            if (mui.getName == name)
                return mui
        }
        null
    }

    lazy val ui = new BoxPanel(Orientation.Vertical) {
        border = Swing.EmptyBorder(10)

        lazy val lblStatus = new Label("Status: nunggu perintah...")
        lazy val btnExit = new Button("Metu")
        lazy val btnHelp = new Button("Tolong")
        lazy val btnRefresh = new Button("Refresh")
        lazy val btnUpdate = new Button("Update")

        lazy val txtLog = new TextArea(9, 40){editable = false}
        lblStatus.horizontalAlignment = Alignment.Left

        val log = Logger(txtLog)

        loadModules(log)


        object tblModel extends AbstractTableModel {
            
            private var modules_ = Array[Array[Any]]()
            def setModules(mods:Array[Array[Any]]){ modules_ = mods }
            def getModules = modules_

            def getColumnCount = 4

            override def getColumnName(p1: Int) = Array("Name", "Version", "Branch", "Modified").apply(p1)

            def getValueAt(row: Int, col: Int): AnyRef = modules_(row)(col).asInstanceOf[AnyRef]

            def getRowCount = modules.length

            override def isCellEditable(p1: Int, p2: Int) = false
            override def setValueAt(value: Any, row: Int, col: Int) {
                modules_(row)(col) = value
                fireTableCellUpdated(row, col)
            }
        }

        //case class Module(name:String, branch:String, modified:Boolean)
        val mods = modules.map{ m =>
            val Seq(branch, modified) = m.getState

            var version = m.getVersion
            if (version == "-"){
                // get version from central module config files
                version = m.getRawModule.getVersion
            }
                
            List(m.getName, version, branch, if (modified=="true"){ "modified" }else{ "-" } ).toArray[Any]
        }
        tblModel.setModules(mods)

        object tblMods extends Table(Array[Array[Any]](), Array("Name", "Version", "Branch", "Modified")){
            focusable = false
            model = tblModel
        }
        object txtModInfo extends TextArea(15, 10){ editable = false }

        tblMods.selection.elementMode = Table.ElementMode.Row

        listenTo(tblMods.selection)

        contents.append(
            new FlowPanel(FlowPanel.Alignment.Left)(lblStatus),
            new BoxPanel(Orientation.Horizontal){
                contents.append(new ScrollPane(tblMods), new ScrollPane(txtModInfo))
            },
            new ScrollPane(txtLog),
            new FlowPanel(FlowPanel.Alignment.Left)(btnRefresh, btnUpdate),
            new FlowPanel(FlowPanel.Alignment.Right)(btnHelp, btnExit))

        log.print("Ready.")

        listenTo(btnRefresh, btnUpdate, btnHelp, btnExit)
        reactions += {
            case ButtonClicked(btn) =>
                btn.text match {
                    case "Metu" =>
                        quit()
                    case "Tolong" =>
                        lblStatus.text = "Status: help"
                    case "Refresh" =>
                        val newModules = modules.map {
                            m =>
                                val Seq(branch, modified) = m.getState

                                var version = m.getVersion
                                if (version == "-") {
                                    // get version from central module config files
                                    version = m.getRawModule.getVersion
                                }

                                List(m.getName, "zzz", branch, if (modified == "true") {
                                    "modified"
                                } else {
                                    "-"
                                }).toArray[Any]
                        } 
                        tblModel.setModules(newModules)
                        tblMods.repaint()
                    case "Update" =>
                        modules.foreach { m =>
                            m.getRawModule match {
                                case rwm:StandardModule =>
                                    rwm.setLogger(log)
                                    rwm.selfUpdateCallback { msg =>
                                        log.info(msg)
                                    }
                            }
                            m.getRawModule.selfUpdate()
                        }
                }
            case TableRowsSelected(_, range, false) =>
                log.info("row: " + tblMods.selection.cells)

                if(!tblMods.selection.cells.isEmpty)
                {
                    val sel = tblMods.selection.cells.toArray.apply(0)
                    //log.info("cells: " + sel)
                    val v = tblMods.model.getValueAt(sel._1, 0)

                    getUiModule(v.toString) match {
                        case m:GitModule =>
                            txtModInfo.text = "LAST 10 COMMIT:\n\n" + m.getLog(10)
                        case _ =>
                            "-"
                    }
                }



            //case TableColumnsSelected(_, range, false) =>
            //    log.info(range.toString())
        }

    }

    def top = new MainFrame {
        title = "Hilda " + Hilda.VERSION

        contents = ui

        //size = new Dimension(500, 300)

        centerOnScreen()
    }

    /*
    val model = Array(List("Mary", "Campione", "Snowboarding", 5, false).toArray,
        List("Alison", "Huml", "Rowing", 5, false).toArray,
        List("Kathy", "Walrath", "Knitting", 5, false).toArray,
        List("Sharon", "Zakhour", "Speed reading", 5, false).toArray,
        List("Philip", "Milne", "Pool", 5, false).toArray)
    /*val model = Array.tabulate(10000) { i =>
      List("Mary", "Campione", "Snowboarding", i, false).toArray
    }*/

    lazy val ui = new BoxPanel(Orientation.Vertical) {
        val table = new Table(model, Array("First Name", "Last Name", "Sport", "# of Years", "Vegetarian")) {
            preferredViewportSize = new Dimension(500, 70)
        }
        //1.6:table.fillsViewportHeight = true
        listenTo(table.selection)

        contents += new ScrollPane(table)
        contents += new Label("Selection Mode")

        def radio(mutex: ButtonGroup, text: String): RadioButton = {
            val b = new RadioButton(text)
            listenTo(b)
            mutex.buttons += b
            contents += b
            b
        }

        val intervalMutex = new ButtonGroup
        val multiInterval = radio(intervalMutex, "Multiple Interval Selection")
        val elementInterval = radio(intervalMutex, "Single Selection")
        val singleInterval = radio(intervalMutex, "Single Interval Selection")
        intervalMutex.select(multiInterval)

        contents += new Label("Selection Options")
        val elemMutex = new ButtonGroup
        val rowSelection = radio(elemMutex, "Row Selection")
        val columnSelection = radio(elemMutex, "Column Selection")
        val cellSelection = radio(elemMutex, "Cell Selection")
        elemMutex.select(rowSelection)

        val output = new TextArea(5, 40) { editable = false }
        contents += new ScrollPane(output)

        def outputSelection() {
            output.append("Lead: " + table.selection.rows.leadIndex + "," +
                    table.selection.columns.leadIndex + ". ")
            output.append("Rows:")
            for (c <- table.selection.rows) output.append(" " + c)
            output.append(". Columns:")
            for (c <- table.selection.columns) output.append(" " + c)
            output.append(".\n")
        }

        reactions += {
            case ButtonClicked(`multiInterval`) =>
                table.selection.intervalMode = Table.IntervalMode.MultiInterval
                if (cellSelection.selected) {
                    elemMutex.select(rowSelection)
                    table.selection.elementMode = Table.ElementMode.None
                }
                cellSelection.enabled = false
            case ButtonClicked(`elementInterval`) =>
                table.selection.intervalMode = Table.IntervalMode.Single
                cellSelection.enabled = true
            case ButtonClicked(`singleInterval`) =>
                table.selection.intervalMode = Table.IntervalMode.SingleInterval
                cellSelection.enabled = true
            case ButtonClicked(`rowSelection`) =>
                if (rowSelection.selected)
                    table.selection.elementMode = Table.ElementMode.Row
            case ButtonClicked(`columnSelection`) =>
                if (columnSelection.selected)
                    table.selection.elementMode = Table.ElementMode.Column
            case ButtonClicked(`cellSelection`) =>
                if (cellSelection.selected)
                    table.selection.elementMode = Table.ElementMode.Cell
            case TableRowsSelected(_, range, false) =>
                output.append("Rows selected, changes: " + range + "\n")
                outputSelection()
            case TableColumnsSelected(_, range, false) =>
                output.append("Columns selected, changes " + range + "\n")
                outputSelection()
        }
    }

    def top = new MainFrame {
        title = "Table Selection"
        contents = ui
    }
    */
}

