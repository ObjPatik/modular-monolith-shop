package edu.cit.verano.supplier;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.util.ArrayList;
import java.util.List;

@JacksonXmlRootElement(localName = "PurchaseOrderList")
class PurchaseOrderListXml {

    @JacksonXmlProperty(localName = "Count")
    private int count;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "PurchaseOrderAck")
    private List<PurchaseOrderAckXml> orders = new ArrayList<>();

    public PurchaseOrderListXml() {}

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<PurchaseOrderAckXml> getOrders() {
        return orders;
    }

    public void setOrders(List<PurchaseOrderAckXml> orders) {
        this.orders = orders;
    }
}
