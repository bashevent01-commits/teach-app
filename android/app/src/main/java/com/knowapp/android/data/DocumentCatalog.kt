package com.knowapp.android.data

data class DocGroup(val key: String, val name: String)

data class DocType(
    val key: String,
    val name: String,
    val group: String,
    val issuedBy: String,
    val purpose: String,
    val partyLabel: String,
)

// The source documents a business keeps as evidence for its stock, grouped as in the owner's guide
object DocumentCatalog {
    val groups = listOf(
        DocGroup("purchasing", "Purchasing & Receiving Stock"),
        DocGroup("movement", "Internal Stock Movement"),
        DocGroup("selling", "Selling & Shipping Stock"),
        DocGroup("returns", "Returns & Adjustments"),
    )

    val types = listOf(
        DocType("purchase_requisition", "Purchase Requisition", "purchasing", "Department / Storekeeper", "Internal request for the business to buy the inventory that is needed.", "Requested by"),
        DocType("purchase_order", "Purchase Order (PO)", "purchasing", "Procurement", "Official order sent to the supplier with quantities, prices and terms.", "Supplier"),
        DocType("delivery_note_in", "Delivery Note / Packing Slip", "purchasing", "Supplier / carrier", "Comes with the shipment and lists the items delivered.", "Supplier"),
        DocType("goods_received_note", "Goods Received Note (GRN)", "purchasing", "Warehouse / Receiving team", "Confirms the goods were inspected, counted and accepted.", "Supplier"),
        DocType("purchase_invoice", "Purchase Invoice", "purchasing", "Supplier", "The supplier's bill asking for payment for delivered goods.", "Supplier"),
        DocType("goods_issue_note", "Goods Issue Note / Stores Requisition", "movement", "Department manager", "Authorises the store to release items for internal use.", "Issued to"),
        DocType("stock_transfer_note", "Stock Transfer Note / Voucher", "movement", "Warehouse supervisor", "Tracks stock moved between stores, branches or warehouses.", "Moved to"),
        DocType("bin_card", "Bin Card / Stock Card", "movement", "Storekeeper", "Shelf record of additions, withdrawals and the current balance.", "Kept by"),
        DocType("stock_count_sheet", "Stock Count Sheet (Stocktake)", "movement", "Audit / count team", "Actual physical counts recorded against the books.", "Counted by"),
        DocType("sales_order", "Sales Order", "selling", "Sales team", "Confirms a customer's order before the stock is picked and packed.", "Customer"),
        DocType("goods_dispatched_note", "Goods Dispatched Note (GDN)", "selling", "Warehouse / dispatch", "Proof the stock was picked, packed and left the store.", "Customer"),
        DocType("delivery_note_out", "Delivery Note (to customer)", "selling", "Dispatch / courier", "Signed by the customer as proof of delivery.", "Customer"),
        DocType("sales_invoice_receipt", "Sales Invoice / Cash Receipt", "selling", "Billing", "Proof of sale, recording what is owed or what was paid.", "Customer"),
        DocType("debit_note", "Debit Note", "returns", "Buyer / accounts payable", "Sent to a supplier when returning defective or wrong goods.", "Supplier"),
        DocType("credit_note", "Credit Note", "returns", "Seller / accounts receivable", "Issued to a customer who returns stock, crediting or refunding them.", "Customer"),
        DocType("stock_adjustment_voucher", "Stock Adjustment Voucher", "returns", "Inventory manager / auditor", "Records manual changes for spoilage, theft, shrinkage or errors.", "Approved by"),
    )

    fun byKey(key: String): DocType? = types.firstOrNull { it.key == key }
    fun inGroup(group: String): List<DocType> = types.filter { it.group == group }
}
