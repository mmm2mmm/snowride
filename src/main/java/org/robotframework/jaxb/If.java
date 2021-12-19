//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2021.11.16 at 04:23:27 PM CET 
//


package org.robotframework.jaxb;

import java.util.LinkedList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for If complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="If">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;choice maxOccurs="unbounded">
 *         &lt;element name="branch" type="{}IfBranch" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="msg" type="{}Message" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="doc" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="status" type="{}BodyItemStatus"/>
 *       &lt;/choice>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "If")
public class If extends OutputElement {

    @XmlElement(name = "branch", type = IfBranch.class)
    protected List<IfBranch> branches = new LinkedList<>();
    @XmlElement(name = "msg", type = String.class)
    protected List<String> msg = new LinkedList<>();
    @XmlElement(name = "doc", type = String.class)
    protected String doc;
    @XmlElement(name = "status", type = BodyItemStatus.class)
    protected BodyItemStatus status;

    @Override
    public String getName() {
        return "IF";
    }

    @Override
    public List<OutputElement> getElements() {
        return new LinkedList<>(branches);
    }

    public List<IfBranch> getBranches() {
        return branches;
    }

    public List<String> getMsg() {
        return msg;
    }

    public String getDoc() {
        return doc;
    }

    public BodyItemStatus getStatus() {
        return status;
    }
}