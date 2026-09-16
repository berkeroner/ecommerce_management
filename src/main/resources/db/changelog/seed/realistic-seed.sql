-- Fictional development data, not real customer information or current market prices.
-- Natural-key guards preserve existing records and avoid hard-coded primary keys.
INSERT INTO categories (name, slug, is_active, created_at, updated_at)
SELECT s.name, s.slug, s.is_active, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    SELECT 'Elektronik' AS name, 'elektronik' AS slug, TRUE AS is_active
    UNION ALL SELECT 'Bilgisayar ve Aksesuar', 'bilgisayar-aksesuar', TRUE
    UNION ALL SELECT 'Ev ve Mutfak', 'ev-mutfak', TRUE
    UNION ALL SELECT 'Giyim', 'giyim', TRUE
    UNION ALL SELECT 'Kitap', 'kitap', TRUE
    UNION ALL SELECT 'Spor ve Outdoor', 'spor-outdoor', TRUE
    UNION ALL SELECT 'Kişisel Bakım', 'kisisel-bakim', TRUE
    UNION ALL SELECT 'Kırtasiye', 'kirtasiye', FALSE
) s
WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE c.slug = s.slug);

INSERT INTO customers (name, email, status, created_at, updated_at)
SELECT s.name, s.email, s.status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    SELECT 'Ayşe Yılmaz' AS name, 'ayse.yilmaz@example.com' AS email, 'ACTIVE' AS status
    UNION ALL SELECT 'Mehmet Kaya', 'mehmet.kaya@example.com', 'ACTIVE'
    UNION ALL SELECT 'Deniz Demir', 'deniz.demir@example.com', 'PASSIVE'
    UNION ALL SELECT 'Elif Şahin', 'elif.sahin@example.com', 'ACTIVE'
    UNION ALL SELECT 'Emre Çelik', 'emre.celik@example.com', 'ACTIVE'
    UNION ALL SELECT 'Zeynep Arslan', 'zeynep.arslan@example.com', 'ACTIVE'
    UNION ALL SELECT 'Burak Koç', 'burak.koc@example.com', 'ACTIVE'
    UNION ALL SELECT 'Selin Aydın', 'selin.aydin@example.com', 'ACTIVE'
    UNION ALL SELECT 'Can Yıldız', 'can.yildiz@example.com', 'PASSIVE'
    UNION ALL SELECT 'Derya Aksoy', 'derya.aksoy@example.com', 'ACTIVE'
    UNION ALL SELECT 'Mert Öztürk', 'mert.ozturk@example.com', 'ACTIVE'
    UNION ALL SELECT 'İrem Yalçın', 'irem.yalcin@example.com', 'ACTIVE'
    UNION ALL SELECT 'Oğuz Tekin', 'oguz.tekin@example.com', 'ACTIVE'
    UNION ALL SELECT 'Ece Karaca', 'ece.karaca@example.com', 'ACTIVE'
    UNION ALL SELECT 'Onur Keskin', 'onur.keskin@example.com', 'PASSIVE'
    UNION ALL SELECT 'Buse Erdoğan', 'buse.erdogan@example.com', 'ACTIVE'
    UNION ALL SELECT 'Kerem Polat', 'kerem.polat@example.com', 'ACTIVE'
    UNION ALL SELECT 'Aslı Güneş', 'asli.gunes@example.com', 'ACTIVE'
    UNION ALL SELECT 'Tolga Acar', 'tolga.acar@example.com', 'ACTIVE'
    UNION ALL SELECT 'Seda Kılıç', 'seda.kilic@example.com', 'ACTIVE'
    UNION ALL SELECT 'Hakan Bulut', 'hakan.bulut@example.com', 'PASSIVE'
    UNION ALL SELECT 'Gizem Eren', 'gizem.eren@example.com', 'ACTIVE'
    UNION ALL SELECT 'Umut Taş', 'umut.tas@example.com', 'ACTIVE'
    UNION ALL SELECT 'Ceren Kurt', 'ceren.kurt@example.com', 'ACTIVE'
) s
WHERE NOT EXISTS (SELECT 1 FROM customers c WHERE c.email = s.email);

INSERT INTO products (category_id, name, sku, price, stock, status, created_at, updated_at)
SELECT c.id, s.name, s.sku, s.price, s.stock, s.status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    SELECT 'elektronik' AS slug, 'Akıllı Telefon 128 GB Siyah' AS name, 'ELK-TEL-1001' AS sku, 18999.90 AS price, 32 AS stock, 'ACTIVE' AS status
    UNION ALL SELECT 'elektronik', 'Akıllı Telefon 256 GB Mavi', 'ELK-TEL-1002', 24999.90, 18, 'ACTIVE'
    UNION ALL SELECT 'elektronik', 'Kablosuz Kulak İçi Kulaklık', 'ELK-KLK-1001', 1499.90, 75, 'ACTIVE'
    UNION ALL SELECT 'elektronik', 'Bluetooth Taşınabilir Hoparlör', 'ELK-HPR-1001', 2299.00, 0, 'ACTIVE'
    UNION ALL SELECT 'elektronik', 'Akıllı Saat Siyah Silikon Kordon', 'ELK-SAT-1001', 3499.90, 21, 'ACTIVE'
    UNION ALL SELECT 'elektronik', 'Taşınabilir Şarj Cihazı 20000 mAh', 'ELK-SRJ-1001', 899.90, 6, 'PASSIVE'
    UNION ALL SELECT 'bilgisayar-aksesuar', 'Laptop 15.6 inç 16 GB RAM 512 GB SSD', 'BLG-LAP-1001', 32999.90, 12, 'ACTIVE'
    UNION ALL SELECT 'bilgisayar-aksesuar', 'Laptop 14 inç 8 GB RAM 256 GB SSD', 'BLG-LAP-1002', 21999.00, 9, 'ACTIVE'
    UNION ALL SELECT 'bilgisayar-aksesuar', 'Mekanik Klavye Türkçe Q', 'BLG-KLV-1001', 1799.90, 35, 'ACTIVE'
    UNION ALL SELECT 'bilgisayar-aksesuar', 'Kablosuz Ergonomik Mouse', 'BLG-MOU-1001', 649.90, 84, 'ACTIVE'
    UNION ALL SELECT 'bilgisayar-aksesuar', '27 inç IPS Monitör', 'BLG-MON-1001', 6999.00, 0, 'ACTIVE'
    UNION ALL SELECT 'bilgisayar-aksesuar', 'USB-C Çoklayıcı 6 Port', 'BLG-HUB-1001', 1199.90, 27, 'ACTIVE'
    UNION ALL SELECT 'ev-mutfak', 'Paslanmaz Çelik Termos 500 ml', 'EVM-TRM-1001', 549.90, 58, 'ACTIVE'
    UNION ALL SELECT 'ev-mutfak', 'Filtre Kahve Makinesi 1.25 L', 'EVM-KHV-1001', 2499.00, 16, 'ACTIVE'
    UNION ALL SELECT 'ev-mutfak', 'Seramik Kupa Seti 4 Parça', 'EVM-KUP-1001', 399.90, 42, 'ACTIVE'
    UNION ALL SELECT 'ev-mutfak', 'LED Masa Lambası Ayarlanabilir', 'EVM-LMB-1001', 799.90, 33, 'ACTIVE'
    UNION ALL SELECT 'ev-mutfak', 'Pamuklu Nevresim Takımı Çift Kişilik', 'EVM-NVR-1001', 1299.00, 23, 'ACTIVE'
    UNION ALL SELECT 'ev-mutfak', 'Cam Saklama Kabı Seti 3 Parça', 'EVM-SKL-1001', 449.90, 7, 'PASSIVE'
    UNION ALL SELECT 'giyim', 'Pamuklu Bisiklet Yaka Tişört Beyaz M', 'GYM-TSH-1001', 299.90, 120, 'ACTIVE'
    UNION ALL SELECT 'giyim', 'Pamuklu Bisiklet Yaka Tişört Siyah L', 'GYM-TSH-1002', 299.90, 95, 'ACTIVE'
    UNION ALL SELECT 'giyim', 'Düz Kesim Jean Pantolon Mavi 32', 'GYM-JEN-1001', 899.90, 38, 'ACTIVE'
    UNION ALL SELECT 'giyim', 'Fermuarlı Kapüşonlu Sweatshirt Gri L', 'GYM-SWT-1001', 749.90, 26, 'ACTIVE'
    UNION ALL SELECT 'giyim', 'Su Geçirmez Yağmurluk Lacivert M', 'GYM-YGM-1001', 1199.90, 0, 'ACTIVE'
    UNION ALL SELECT 'giyim', 'Pamuklu Çorap Seti 5 Çift', 'GYM-CRP-1001', 199.90, 160, 'ACTIVE'
    UNION ALL SELECT 'kitap', 'Java ile Nesne Yönelimli Programlama', 'KTP-JAV-1001', 459.90, 48, 'ACTIVE'
    UNION ALL SELECT 'kitap', 'Spring Boot ile REST API Geliştirme', 'KTP-SPR-1001', 529.90, 31, 'ACTIVE'
    UNION ALL SELECT 'kitap', 'Veritabanı Tasarımı ve SQL', 'KTP-SQL-1001', 389.90, 44, 'ACTIVE'
    UNION ALL SELECT 'kitap', 'Algoritmalar ve Veri Yapıları', 'KTP-ALG-1001', 499.90, 19, 'ACTIVE'
    UNION ALL SELECT 'kitap', 'Dünya Klasikleri Seçkisi 3 Kitap', 'KTP-KLS-1001', 349.90, 62, 'ACTIVE'
    UNION ALL SELECT 'kitap', 'Çocuklar İçin Bilim Atlası', 'KTP-ATL-1001', 279.90, 5, 'PASSIVE'
    UNION ALL SELECT 'spor-outdoor', 'Kaymaz Yoga Matı 6 mm', 'SPR-YOG-1001', 499.90, 37, 'ACTIVE'
    UNION ALL SELECT 'spor-outdoor', 'Ayarlanabilir Dambıl Seti 20 kg', 'SPR-DMB-1001', 2499.90, 14, 'ACTIVE'
    UNION ALL SELECT 'spor-outdoor', 'Paslanmaz Çelik Sporcu Suluk 750 ml', 'SPR-SLK-1001', 349.90, 70, 'ACTIVE'
    UNION ALL SELECT 'spor-outdoor', 'Outdoor Sırt Çantası 30 L', 'SPR-CNT-1001', 1399.90, 22, 'ACTIVE'
    UNION ALL SELECT 'spor-outdoor', 'Direnç Bandı Seti 5 Parça', 'SPR-BNT-1001', 299.90, 0, 'ACTIVE'
    UNION ALL SELECT 'spor-outdoor', 'Kamp Sandalyesi Katlanabilir', 'SPR-SND-1001', 899.90, 28, 'ACTIVE'
    UNION ALL SELECT 'kisisel-bakim', 'Nemlendirici Yüz Kremi 50 ml', 'BKM-KRM-1001', 249.90, 88, 'ACTIVE'
    UNION ALL SELECT 'kisisel-bakim', 'Güneş Koruyucu SPF 50 100 ml', 'BKM-GNS-1001', 399.90, 54, 'ACTIVE'
    UNION ALL SELECT 'kisisel-bakim', 'Sülfatsız Şampuan 400 ml', 'BKM-SMP-1001', 189.90, 102, 'ACTIVE'
    UNION ALL SELECT 'kisisel-bakim', 'Elektrikli Diş Fırçası', 'BKM-DFR-1001', 1299.90, 17, 'ACTIVE'
    UNION ALL SELECT 'kisisel-bakim', 'El ve Vücut Losyonu 250 ml', 'BKM-LSY-1001', 159.90, 65, 'ACTIVE'
    UNION ALL SELECT 'kisisel-bakim', 'Sakal Bakım Yağı 30 ml', 'BKM-YAG-1001', 219.90, 8, 'PASSIVE'
    UNION ALL SELECT 'kirtasiye', 'A5 Çizgili Defter 120 Yaprak', 'KRT-DFT-1001', 79.90, 145, 'PASSIVE'
    UNION ALL SELECT 'kirtasiye', 'Jel Kalem Seti 6 Renk', 'KRT-KLM-1001', 129.90, 92, 'PASSIVE'
    UNION ALL SELECT 'kirtasiye', 'Masaüstü Organizer Ahşap', 'KRT-ORG-1001', 249.90, 36, 'PASSIVE'
    UNION ALL SELECT 'kirtasiye', 'A4 Fotokopi Kağıdı 500 Yaprak', 'KRT-KGT-1001', 179.90, 64, 'PASSIVE'
    UNION ALL SELECT 'kirtasiye', 'Okul Kalem Kutusu Fermuarlı', 'KRT-KUT-1001', 149.90, 52, 'PASSIVE'
    UNION ALL SELECT 'kirtasiye', 'Yapışkanlı Not Kağıdı Seti', 'KRT-NOT-1001', 59.90, 180, 'PASSIVE'
) s
JOIN categories c ON c.slug = s.slug
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.sku = s.sku);

INSERT INTO addresses (addressable_type, addressable_id, address_type, title, city, district, address_line, postal_code, created_at, updated_at)
SELECT 'CUSTOMER', c.id, 'SHIPPING', 'Ev', s.city, s.district, s.address_line, s.postal_code, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    SELECT 'ayse.yilmaz@example.com' AS email, 'İstanbul' AS city, 'Kadıköy' AS district, 'Caferağa Mahallesi, Moda Caddesi No: 18 Daire: 4' AS address_line, '34710' AS postal_code
    UNION ALL SELECT 'mehmet.kaya@example.com', 'Ankara', 'Çankaya', 'Bahçelievler Mahallesi, 54. Sokak No: 12 Daire: 7', '06490'
    UNION ALL SELECT 'deniz.demir@example.com', 'İzmir', 'Bornova', 'Kazımdirik Mahallesi, 162. Sokak No: 8 Daire: 3', '35100'
    UNION ALL SELECT 'elif.sahin@example.com', 'İstanbul', 'Üsküdar', 'Acıbadem Mahallesi, Çamlıca Sokak No: 26 Daire: 5', '34660'
    UNION ALL SELECT 'emre.celik@example.com', 'Bursa', 'Nilüfer', 'İhsaniye Mahallesi, İzmir Yolu Caddesi No: 42 Daire: 2', '16130'
    UNION ALL SELECT 'zeynep.arslan@example.com', 'Antalya', 'Muratpaşa', 'Fener Mahallesi, 1964. Sokak No: 15 Daire: 6', '07160'
    UNION ALL SELECT 'burak.koc@example.com', 'İstanbul', 'Ataşehir', 'Atatürk Mahallesi, Ataşehir Bulvarı No: 34 Daire: 9', '34758'
    UNION ALL SELECT 'selin.aydin@example.com', 'Ankara', 'Yenimahalle', 'Batıkent Mahallesi, 2026. Cadde No: 11 Daire: 4', '06370'
    UNION ALL SELECT 'can.yildiz@example.com', 'İzmir', 'Karşıyaka', 'Bostanlı Mahallesi, 2018. Sokak No: 23 Daire: 8', '35590'
    UNION ALL SELECT 'derya.aksoy@example.com', 'Eskişehir', 'Tepebaşı', 'Batıkent Mahallesi, Gündüz Ökçün Bulvarı No: 17 Daire: 3', '26180'
    UNION ALL SELECT 'mert.ozturk@example.com', 'İstanbul', 'Beşiktaş', 'Abbasağa Mahallesi, Şair Leyla Sokak No: 9 Daire: 2', '34353'
    UNION ALL SELECT 'irem.yalcin@example.com', 'Kocaeli', 'İzmit', 'Yahya Kaptan Mahallesi, Akasyalar Caddesi No: 21 Daire: 5', '41050'
    UNION ALL SELECT 'oguz.tekin@example.com', 'Samsun', 'Atakum', 'Mimarsinan Mahallesi, 160. Sokak No: 14 Daire: 7', '55200'
    UNION ALL SELECT 'ece.karaca@example.com', 'Konya', 'Selçuklu', 'Bosna Hersek Mahallesi, Yeni İstanbul Caddesi No: 36 Daire: 4', '42250'
    UNION ALL SELECT 'onur.keskin@example.com', 'Kayseri', 'Melikgazi', 'Alpaslan Mahallesi, Bahar Caddesi No: 19 Daire: 6', '38030'
    UNION ALL SELECT 'buse.erdogan@example.com', 'İstanbul', 'Maltepe', 'Altayçeşme Mahallesi, Bağdat Caddesi No: 52 Daire: 10', '34843'
    UNION ALL SELECT 'kerem.polat@example.com', 'Ankara', 'Etimesgut', 'Eryaman Mahallesi, 3. Cadde No: 28 Daire: 12', '06824'
    UNION ALL SELECT 'asli.gunes@example.com', 'İzmir', 'Buca', 'Adatepe Mahallesi, 1. Sokak No: 16 Daire: 3', '35400'
    UNION ALL SELECT 'tolga.acar@example.com', 'Bursa', 'Osmangazi', 'Çekirge Mahallesi, Uludağ Caddesi No: 31 Daire: 5', '16070'
    UNION ALL SELECT 'seda.kilic@example.com', 'Antalya', 'Konyaaltı', 'Hurma Mahallesi, 241. Sokak No: 22 Daire: 8', '07130'
    UNION ALL SELECT 'hakan.bulut@example.com', 'Adana', 'Çukurova', 'Güzelyalı Mahallesi, Turgut Özal Bulvarı No: 45 Daire: 9', '01170'
    UNION ALL SELECT 'gizem.eren@example.com', 'Mersin', 'Yenişehir', 'Menteş Mahallesi, Üniversite Caddesi No: 27 Daire: 4', '33150'
    UNION ALL SELECT 'umut.tas@example.com', 'Trabzon', 'Ortahisar', 'Beşirli Mahallesi, Devlet Sahil Yolu Caddesi No: 13 Daire: 2', '61040'
    UNION ALL SELECT 'ceren.kurt@example.com', 'Gaziantep', 'Şehitkamil', 'İbrahimli Mahallesi, 100. Yıl Caddesi No: 38 Daire: 6', '27060'
) s
JOIN customers c ON c.email = s.email
WHERE NOT EXISTS (SELECT 1 FROM addresses a WHERE a.addressable_type = 'CUSTOMER'
    AND a.addressable_id = c.id AND a.address_type = 'SHIPPING' AND a.title = 'Ev');

-- Six customers use the same home address for invoicing and shipping.
INSERT INTO addresses (addressable_type, addressable_id, address_type, title, city, district, address_line, postal_code, created_at, updated_at)
SELECT 'CUSTOMER', c.id, 'BILLING', 'Fatura Adresi', a.city, a.district, a.address_line, a.postal_code, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM customers c
JOIN addresses a ON a.addressable_type = 'CUSTOMER' AND a.addressable_id = c.id
    AND a.address_type = 'SHIPPING' AND a.title = 'Ev'
WHERE c.email IN ('ayse.yilmaz@example.com', 'mehmet.kaya@example.com', 'deniz.demir@example.com',
    'elif.sahin@example.com', 'emre.celik@example.com', 'zeynep.arslan@example.com')
AND NOT EXISTS (SELECT 1 FROM addresses existing WHERE existing.addressable_type = 'CUSTOMER'
    AND existing.addressable_id = c.id AND existing.address_type = 'BILLING' AND existing.title = 'Fatura Adresi');
