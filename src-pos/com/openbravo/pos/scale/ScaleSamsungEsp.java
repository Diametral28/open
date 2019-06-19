//    Openbravo POS is a point of sales application designed for touch screens.
//    Copyright (C) 2007-2009 Openbravo, S.L.
//    http://www.openbravo.com/product/pos
//
//    This file is part of Openbravo POS.
//
//    Openbravo POS is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    Openbravo POS is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with Openbravo POS.  If not, see <http://www.gnu.org/licenses/>.

package com.openbravo.pos.scale;

import gnu.io.*;
import java.io.*;
import java.util.TooManyListenersException;


public class ScaleSamsungEsp implements Scale, SerialPortEventListener {

    private CommPortIdentifier m_PortIdPrinter;
    private SerialPort m_CommPortPrinter;

    private String m_sPortScale;
    private OutputStream m_out;
    private InputStream m_in;

    private static final int SCALE_READY = 0;
    private static final int SCALE_READING = 1;
    private static final int SCALE_READINGDECIMALS = 2;

    private double m_dWeightBuffer;
    private double m_dWeightDecimals;
    private int m_iStatusScale;

    /**
     * Creates a new instance of ScaleComm
     */
    public ScaleSamsungEsp(String sPortPrinter) {
        System.out.println("Iniciamos la inicialización de valores...");
        m_sPortScale = sPortPrinter;
        m_out = null;
        m_in = null;

        m_iStatusScale = SCALE_READY;
        m_dWeightBuffer = 0.0;
        m_dWeightDecimals = 1.0;
        System.out.println("Finalizamos la inicializacion de valores...");
    }

    @Override
    public Double readWeight() {

        synchronized (this) {

            if (m_iStatusScale != SCALE_READY) {
                try {
                    System.out.println("Que se espere 1 segundo la báscula...");
                    wait(1000);
                } catch (InterruptedException e) {
                }
                if (m_iStatusScale != SCALE_READY) {
                    // bascula tonta.
                    m_iStatusScale = SCALE_READY;
                }
            }

            // Ya estamos en SCALE_READY
            m_dWeightBuffer = 0.0;
            m_dWeightDecimals = 1.0;

            System.out.println("Mandamos la puta W");
            write(new byte[]{0x50}); // W
//            System.out.println("Limpiamos...");
//            flush();
            System.out.println("Mandamos a que se detenga...");
            write(new byte[]{0x000D});
//            System.out.println("Otra vez limpiamos...");
//            flush();

            // Esperamos un ratito
            try {
                System.out.println("Esperamos otro segundo conchas...");
                wait(1000);
            } catch (InterruptedException e) {
            }

            if (m_iStatusScale == SCALE_READY) {
                System.out.println("Recibimos cosas...");
                System.out.println(m_dWeightBuffer +"----"+m_dWeightDecimals);
                // hemos recibido cositas o si no hemos recibido nada estamos a 0.0
                double dWeight = m_dWeightBuffer / m_dWeightDecimals;
                m_dWeightBuffer = 0.0;
                m_dWeightDecimals = 1.0;
                
                return new Double(dWeight);
            } else {
                System.out.println("No recibimos ni una madre...");
                m_iStatusScale = SCALE_READY;
                m_dWeightBuffer = 0.0;
                m_dWeightDecimals = 1000.0;
                return new Double(0.0);
            }
        }
    }

    private void flush() {
        try {
            m_out.flush();
        } catch (IOException e) {
        }
    }

    private void write(byte[] data) {
        try {
            if (m_out == null) {
                try{
                System.out.println("No esta abierto el puerco... lo abrimos...");
                m_PortIdPrinter = CommPortIdentifier.getPortIdentifier(m_sPortScale); // Tomamos el puerto                   
                m_CommPortPrinter = (SerialPort) m_PortIdPrinter.open("PORTID", 2000); // Abrimos el puerto       
                m_out = m_CommPortPrinter.getOutputStream(); // Tomamos el chorro de escritura   
                m_in = m_CommPortPrinter.getInputStream();

                m_CommPortPrinter.addEventListener(this);
                m_CommPortPrinter.notifyOnDataAvailable(true);

                m_CommPortPrinter.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE); 
                } catch (Exception e) {
                    System.err.println(e.toString());
                }
            } else {
                System.out.println("Ya estaba abierto...");
            }
            System.out.println("Mandamos datitos...");
            System.out.println(data);
            m_out.write(data);
            flush();
//            m_CommPortPrinter.close();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    @Override
    public void serialEvent(SerialPortEvent e) {

        // Determine type of event.
        switch (e.getEventType()) {
            case SerialPortEvent.BI:
            case SerialPortEvent.OE:
            case SerialPortEvent.FE:
            case SerialPortEvent.PE:
            case SerialPortEvent.CD:
            case SerialPortEvent.CTS:
            case SerialPortEvent.DSR:
            case SerialPortEvent.RI:
            case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
                break;
            case SerialPortEvent.DATA_AVAILABLE:
                try {
                    //TODO: aqui esta la verga...
                    while (m_in.available() > 0) {
                        int b = m_in.read();
                        System.out.println("Quien sabe para que es b pero vale { " + b + " }");
                        if (b == 0x000D) { // CR ASCII
                            // Fin de lectura
                            System.out.println("FIn...");
                            synchronized (this) {
                                m_iStatusScale = SCALE_READY;
                                notifyAll();
                            }
                        } else if ((b > 0x002F && b < 0x003A) || b == 0x002E) {
                            synchronized (this) {
                                if (m_iStatusScale == SCALE_READY) {
                                    System.out.println("Bascula lista...");
                                    m_dWeightBuffer = 0.0; // se supone que esto debe estar ya garantizado
                                    m_dWeightDecimals = 1.0;
                                    m_iStatusScale = SCALE_READING;
                                    System.out.println(m_iStatusScale);
                                }
                                if (b == 0x002E) {
                                    System.out.println("Bascula leyendo..");
                                    m_iStatusScale = SCALE_READINGDECIMALS;
                                } else {
                                    System.out.println("Haciendo cuentitas...");
                                    //Ya me di cuenta que aquí se va creando la cantidad... si veo que es mayor sigo asignando el valor si no pa que regreso a 0s xD
                                    if(m_dWeightBuffer < (m_dWeightBuffer * 10.0 + b - 0x0030)) {
                                        m_dWeightBuffer = m_dWeightBuffer * 10.0 + b - 0x0030;
                                    }
                                    System.out.println("El valor del peso es: " + m_dWeightBuffer);
                                    if (m_iStatusScale == SCALE_READINGDECIMALS) {
                                        m_dWeightDecimals *= 10.0;
                                        System.out.println("El valor de m_dWiihtDecimal" + m_dWeightDecimals);
                                    }
                                }
                            }
                        }
//                        else {
//                            System.out.println("Valio madres... reseteamos valores...");
//                            // caracteres invalidos, reseteamos.
//                            m_dWeightBuffer = 0.0; // se supone que esto debe estar ya garantizado
//                            m_dWeightDecimals = 1000.0;
//                            m_iStatusScale = SCALE_READY;
//                        }
                    }
//                    m_out.close();
//                    m_in.close();
//                    m_CommPortPrinter.close();
                } catch (IOException eIO) {
                }

                break;
        }
    }
}