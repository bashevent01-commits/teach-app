# The source documents a business keeps as evidence for its stock, grouped as in the guide the owner supplied
DOCUMENT_GROUPS = {
    "purchasing": "Purchasing & Receiving Stock",
    "movement": "Internal Stock Movement",
    "selling": "Selling & Shipping Stock",
    "returns": "Returns & Adjustments",
}

DOCUMENT_TYPES = [
    # key, name, group, issued/created by, purpose, label for the other party
    ("purchase_requisition", "Purchase Requisition", "purchasing", "Department / Storekeeper", "Internal request for the business to buy the inventory that is needed.", "Requested by"),
    ("purchase_order", "Purchase Order (PO)", "purchasing", "Procurement", "Official order sent to the supplier with quantities, prices and terms.", "Supplier"),
    ("delivery_note_in", "Delivery Note / Packing Slip", "purchasing", "Supplier / carrier", "Comes with the shipment and lists the items delivered.", "Supplier"),
    ("goods_received_note", "Goods Received Note (GRN)", "purchasing", "Warehouse / Receiving team", "Confirms the goods were inspected, counted and accepted.", "Supplier"),
    ("purchase_invoice", "Purchase Invoice", "purchasing", "Supplier", "The supplier's bill asking for payment for delivered goods.", "Supplier"),
    ("goods_issue_note", "Goods Issue Note / Stores Requisition", "movement", "Department manager", "Authorises the store to release items for internal use.", "Issued to"),
    ("stock_transfer_note", "Stock Transfer Note / Voucher", "movement", "Warehouse supervisor", "Tracks stock moved between stores, branches or warehouses.", "Moved to"),
    ("bin_card", "Bin Card / Stock Card", "movement", "Storekeeper", "Shelf record of additions, withdrawals and the current balance.", "Kept by"),
    ("stock_count_sheet", "Stock Count Sheet (Stocktake)", "movement", "Audit / count team", "Actual physical counts recorded against the books.", "Counted by"),
    ("sales_order", "Sales Order", "selling", "Sales team", "Confirms a customer's order before the stock is picked and packed.", "Customer"),
    ("goods_dispatched_note", "Goods Dispatched Note (GDN)", "selling", "Warehouse / dispatch", "Proof the stock was picked, packed and left the store.", "Customer"),
    ("delivery_note_out", "Delivery Note (to customer)", "selling", "Dispatch / courier", "Signed by the customer as proof of delivery.", "Customer"),
    ("sales_invoice_receipt", "Sales Invoice / Cash Receipt", "selling", "Billing", "Proof of sale, recording what is owed or what was paid.", "Customer"),
    ("debit_note", "Debit Note", "returns", "Buyer / accounts payable", "Sent to a supplier when returning defective or wrong goods.", "Supplier"),
    ("credit_note", "Credit Note", "returns", "Seller / accounts receivable", "Issued to a customer who returns stock, crediting or refunding them.", "Customer"),
    ("stock_adjustment_voucher", "Stock Adjustment Voucher", "returns", "Inventory manager / auditor", "Records manual changes for spoilage, theft, shrinkage or errors.", "Approved by"),
]

DOCUMENT_KEYS = {t[0] for t in DOCUMENT_TYPES}
DOCUMENT_NAMES = {t[0]: t[1] for t in DOCUMENT_TYPES}
