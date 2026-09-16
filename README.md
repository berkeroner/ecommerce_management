**Stajyer Projesi: Mini E-Ticaret Sipariş API’si**  
Docker üzerinde çalışan, asenkron işlemleri destekleyen basit bir
e-ticaret API’si geliştirilecektir.  
**Kullanılacak servisler**

||
||
||

<table>
<tbody>
<tr class="odd">
<td><ul>
<li><p>MySQL: Kalıcı veriler</p></li>
<li><p>Redis: Cache, stok kilidi ve idempotency</p></li>
<li><p>RabbitMQ: Asenkron event ve job işlemleri</p></li>
<li><p>Tercih edilen bir API framework’ü</p></li>
<li><p>Docker Compose<br />
<strong>Temel senaryo</strong><br />
Müşteri ürünleri listeleyebilir ve sipariş oluşturabilir. Sipariş
oluşturulduktan sonra stok kontrolü, ödeme ve bildirim süreçleri
RabbitMQ üzerinden asenkron olarak çalıştırılır.<br />
Ödeme yöntemi ve kargo firması çalışma zamanında seçilebilmelidir. Bunun
için Strategy Pattern kullanılmalıdır.<br />
Ürün, sipariş veya ödeme gibi farklı modellere dosya eklenebilmelidir.
Dosya ilişkisi polymorphic olarak tasarlanmalıdır.<br />
<br />
<strong>Veri tabanı tabloları</strong><br />
customers</p></li>
</ul>
<p>Alan</p></td>
<td><p>Tip</p></td>
<td><p>Açıklama</p></td>
</tr>
<tr class="even">
<td><p>id</p></td>
<td><p>bigint</p></td>
<td><p>Primary key</p></td>
</tr>
<tr class="odd">
<td><p>name</p></td>
<td><p>varchar(150)</p></td>
<td><p>Müşteri adı</p></td>
</tr>
<tr class="even">
<td><p>email</p></td>
<td><p>varchar(190)</p></td>
<td><p>Unique</p></td>
</tr>
<tr class="odd">
<td><p>status</p></td>
<td><p>varchar(30)</p></td>
<td><p>active, passive</p></td>
</tr>
<tr class="even">
<td><p>created_at</p></td>
<td><p>datetime</p></td>
<td><p>Oluşturulma tarihi</p></td>
</tr>
<tr class="odd">
<td><p>updated_at</p></td>
<td><p>datetime</p></td>
<td><p>Güncellenme tarihi</p></td>
</tr>
<tr class="even">
<td></td>
<td></td>
<td></td>
</tr>
</tbody>
</table>

**categories**

|            |              |                    |
|------------|--------------|--------------------|
| Alan       | Tip          | Açıklama           |
| id         | bigint       | Primary key        |
| name       | varchar(150) | Kategori adı       |
| slug       | varchar(190) | Unique             |
| created_at | datetime     | Oluşturulma tarihi |
| updated_at | datetime     | Güncellenme tarihi |
|            |              |                    |

**products**

|             |               |                    |
|-------------|---------------|--------------------|
| Alan        | Tip           | Açıklama           |
| id          | bigint        | Primary key        |
| category_id | bigint        | Foreign key        |
| name        | varchar(190)  | Ürün adı           |
| sku         | varchar(100)  | Unique stok kodu   |
| price       | decimal(12,2) | Güncel fiyat       |
| stock       | int           | Satılabilir stok   |
| status      | varchar(30)   | active, passive    |
| created_at  | datetime      | Oluşturulma tarihi |
| updated_at  | datetime      | Güncellenme tarihi |
|             |               |                    |

**orders**

|                |               |                                                   |
|----------------|---------------|---------------------------------------------------|
| Alan           | Tip           | Açıklama                                          |
| id             | bigint        | Primary key                                       |
| order_no       | varchar(50)   | Unique sipariş numarası                           |
| customer_id    | bigint        | Foreign key                                       |
| status         | varchar(30)   | pending, processing, confirmed, failed, cancelled |
| currency       | char(3)       | TRY, USD vb.                                      |
| subtotal       | decimal(12,2) | İndirimsiz toplam                                 |
| discount_total | decimal(12,2) | Toplam indirim                                    |
| shipping_total | decimal(12,2) | Kargo bedeli                                      |
| grand_total    | decimal(12,2) | Ödenecek tutar                                    |
| created_at     | datetime      | Oluşturulma tarihi                                |
| updated_at     | datetime      | Güncellenme tarihi                                |
|                |               |                                                   |

**order_items**  
Sipariş sırasında ürün fiyatı değişebileceği için ürün adı, SKU ve fiyat
bilgileri snapshot olarak tutulmalıdır.

|                |               |                              |
|----------------|---------------|------------------------------|
| Alan           | Tip           | Açıklama                     |
| id             | bigint        | Primary key                  |
| order_id       | bigint        | Foreign key                  |
| product_id     | bigint        | Foreign key                  |
| product_name   | varchar(190)  | Sipariş anındaki ürün adı    |
| sku            | varchar(100)  | Sipariş anındaki SKU         |
| unit_price     | decimal(12,2) | Sipariş anındaki birim fiyat |
| quantity       | int           | Adet                         |
| discount_total | decimal(12,2) | Satır indirimi               |
| line_total     | decimal(12,2) | Satır toplamı                |
| created_at     | datetime      | Oluşturulma tarihi           |
|                |               |                              |

**addresses**  
Adres tablosu polymorphic tasarlanacaktır. Böylece hem müşteriye hem de
siparişe adres bağlanabilir.

|                  |              |                       |
|------------------|--------------|-----------------------|
| Alan             | Tip          | Açıklama              |
| id               | bigint       | Primary key           |
| addressable_type | varchar(100) | customer veya order   |
| addressable_id   | bigint       | İlişkili kaydın ID’si |
| address_type     | varchar(30)  | billing, shipping     |
| title            | varchar(100) | Ev, İş vb.            |
| city             | varchar(100) | Şehir                 |
| district         | varchar(100) | İlçe                  |
| address_line     | text         | Açık adres            |
| postal_code      | varchar(20)  | Posta kodu            |
| created_at       | datetime     | Oluşturulma tarihi    |
| updated_at       | datetime     | Güncellenme tarihi    |
|                  |              |                       |

`addressable_type` ve `addressable_id` alanları için birleşik index
oluşturulmalıdır.  
**payments**

|                |                    |                                                  |
|----------------|--------------------|--------------------------------------------------|
| Alan           | Tip                | Açıklama                                         |
| id             | bigint             | Primary key                                      |
| order_id       | bigint             | Foreign key                                      |
| payment_no     | varchar(50)        | Unique                                           |
| method         | varchar(30)        | credit_card, bank_transfer, cash_on_delivery     |
| provider       | varchar(50)        | mockpay, bank vb.                                |
| status         | varchar(30)        | pending, processing, completed, failed, refunded |
| amount         | decimal(12,2)      | Ödeme tutarı                                     |
| transaction_id | varchar(190)       | Sağlayıcı işlem numarası                         |
| failure_reason | varchar(500)       | Hata açıklaması                                  |
| paid_at        | datetime, nullable | Ödeme tarihi                                     |
| created_at     | datetime           | Oluşturulma tarihi                               |
| updated_at     | datetime           | Güncellenme tarihi                               |
|                |                    |                                                  |

Kart numarası, CVV veya hassas ödeme bilgileri veri tabanında
tutulmamalıdır.  
**shipments**

|                 |                    |                                                |
|-----------------|--------------------|------------------------------------------------|
| Alan            | Tip                | Açıklama                                       |
| id              | bigint             | Primary key                                    |
| order_id        | bigint             | Foreign key                                    |
| provider        | varchar(50)        | yurtiçi, aras, mng, mock                       |
| status          | varchar(30)        | pending, preparing, shipped, delivered, failed |
| tracking_number | varchar(100)       | Kargo takip numarası                           |
| shipping_cost   | decimal(12,2)      | Kargo bedeli                                   |
| shipped_at      | datetime, nullable | Kargoya verilme tarihi                         |
| delivered_at    | datetime, nullable | Teslim tarihi                                  |
| created_at      | datetime           | Oluşturulma tarihi                             |
| updated_at      | datetime           | Güncellenme tarihi                             |
|                 |                    |                                                |

**attachments**  
Ürün, sipariş ve ödeme gibi farklı kayıtlara dosya bağlayabilen
polymorphic tablo.

|                 |              |                             |
|-----------------|--------------|-----------------------------|
| Alan            | Tip          | Açıklama                    |
| id              | bigint       | Primary key                 |
| attachable_type | varchar(100) | product, order veya payment |
| attachable_id   | bigint       | İlişkili kaydın ID’si       |
| type            | varchar(30)  | image, invoice, receipt     |
| original_name   | varchar(255) | Dosya adı                   |
| path            | varchar(500) | Dosya yolu                  |
| mime_type       | varchar(100) | MIME tipi                   |
| size            | bigint       | Dosya boyutu                |
| created_at      | datetime     | Oluşturulma tarihi          |
|                 |              |                             |

`attachable_type` ve `attachable_id` alanları için birleşik index
oluşturulmalıdır.  
**order_status_histories**

|                 |              |                   |
|-----------------|--------------|-------------------|
| Alan            | Tip          | Açıklama          |
| id              | bigint       | Primary key       |
| order_id        | bigint       | Foreign key       |
| previous_status | varchar(30)  | Önceki durum      |
| new_status      | varchar(30)  | Yeni durum        |
| reason          | varchar(500) | Değişiklik nedeni |
| created_at      | datetime     | Değişiklik tarihi |
|                 |              |                   |

**outbox_events**  
Veri tabanı işlemi başarılı olmadan RabbitMQ mesajı gönderilmesini
engellemek amacıyla Outbox Pattern uygulanacaktır.

|                |                    |                            |
|----------------|--------------------|----------------------------|
| Alan           | Tip                | Açıklama                   |
| id             | char(36)           | UUID                       |
| aggregate_type | varchar(100)       | order, payment vb.         |
| aggregate_id   | bigint             | İlgili kaydın ID’si        |
| event_type     | varchar(150)       | order.created vb.          |
| payload        | json               | Event içeriği              |
| status         | varchar(30)        | pending, published, failed |
| retry_count    | int                | Deneme sayısı              |
| published_at   | datetime, nullable | Yayınlanma tarihi          |
| created_at     | datetime           | Oluşturulma tarihi         |

  
**API endpointleri**  
Ürünler  
Ürün listeleme  
`GET /api/v1/products`  
Desteklenecek parametreler:

- `page`

- `limit`

- `category_id`

- `status`

- `search`

- `sort`  
  Ürün listesi Redis üzerinde kısa süreli cache’lenebilir.  
  **Ürün detayı**  
  `GET /api/v1/products/``{``productId``}`  
  **Ürün oluşturma**  
  `POST /api/v1/products`  
  `{`  
  `  "category_id": 1,`  
  `  "name": "Mekanik Klavye",`  
  `  "sku": "KEYBOARD-001",`  
  `  "price": 2500,`  
  `  "stock": 20`  
  `}`  
  **Siparişler**  
  Sipariş oluşturma  
  `POST /api/v1/orders`  
  Header:  
  `Idempotency-Key: 81d66fca-342d-42e4-855b-9a9975a08336`  
  Body:  
  `{`  
  `  "customer_id": 1,`  
  `  "payment_method": "credit_card",`  
  `  "shipping_provider": "mock",`  
  `  "items": [`  
  `    ``{`  
  `      "product_id": 10,`  
  `      "quantity": 2`  
  `    ``}``,`  
  `    ``{`  
  `      "product_id": 15,`  
  `      "quantity": 1`  
  `    ``}`  
  `  ],`  
  `  "shipping_address": ``{`  
  `    "title": "Ev",`  
  `    "city": "``İ``stanbul",`  
  `    "district": "Kad``ı``k``ö``y",`  
  `    "address_line": "``Ö``rnek Mahallesi, Test Sokak No: 1",`  
  `    "postal_code": "34710"`  
  `  ``}`  
  `}`  
  Örnek cevap:  
  `{`  
  `  "data": ``{`  
  `    "id": 125,`  
  `    "order_no": "ORD-20260831-000125",`  
  `    "status": "processing",`  
  `    "grand_total": 6250,`  
  `    "currency": "TRY"`  
  `  ``}`  
  `}`  
  Aynı `Idempotency-Key` ile tekrar istek yapılırsa yeni sipariş
  oluşturulmamalı, önceki cevap dönülmelidir.  
  **Sipariş detayı**  
  `GET /api/v1/orders/``{``orderId``}`  
  **Sipariş iptali**  
  `POST /api/v1/orders/``{``orderId``}``/cancel`  
  Yalnızca uygun durumdaki siparişler iptal edilebilmelidir.  
  **Sipariş durumu**  
  `GET /api/v1/orders/``{``orderId``}``/status`  
  **Ödemeler**  
  Ödeme başlatma  
  `POST /api/v1/orders/``{``orderId``}``/payments`  
  `{`  
  `  "method": "credit_card",`  
  `  "payment_token": "mock-token-123"`  
  `}`  
  **Ödeme iadesi**  
  `POST /api/v1/payments/``{``paymentId``}``/refund`  
  **Dosyalar**  
  Dosya ekleme  
  `POST /api/v1/attachments`  
  `multipart/form-data` kullanılabilir.  
  Alanlar:  
  `attachable_type=order`  
  `attachable_id=125`  
  `type=invoice`  
  `file=<binary>`  
    
  **Sipariş oluşturma akışı**

1.  İstek doğrulanır.

2.  `Idempotency-Key` Redis üzerinde kontrol edilir.

3.  Ürünler ve stok miktarları MySQL üzerinden kontrol edilir.

4.  Stok güncellemesi sırasında Redis distributed lock veya atomik stok
    mekanizması kullanılır.

5.  Sipariş, sipariş kalemleri ve teslimat adresi transaction içinde
    oluşturulur.

6.  Aynı transaction içerisinde `order.created` kaydı `outbox_events`
    tablosuna yazılır.

7.  Outbox worker event’i RabbitMQ’ya gönderir.

8.  Ödeme consumer’ı uygun ödeme stratejisini çalıştırır.

9.  Ödeme başarılıysa `payment.completed`, başarısızsa `payment.failed`
    event’i yayınlanır.

10. Başarılı ödeme sonrasında sipariş onaylanır ve kargo kaydı
    oluşturulur.

11. Bildirim consumer’ı müşteriye gönderilecek e-posta veya mesajı
    simüle eder.  
      
    **RabbitMQ eventleri**  
    En az aşağıdaki eventler desteklenmelidir:

- `order.created`

- `stock.reserved`

- `stock.reservation_failed`

- `payment.requested`

- `payment.completed`

- `payment.failed`

- `order.confirmed`

- `order.cancelled`

- `shipment.created`

- `notification.requested`  
  Event formatı:  
  `{`  
  `  "event_id": "be2f997a-bbea-4483-9913-8db7ac206c68",`  
  `  "event_type": "order.created",`  
  `  "occurred_at": "2026-08-31T12:00:00Z",`  
  `  "correlation_id": "12c26a80-a8eb-49db-821d-c10e27a68f28",`  
  `  "data": ``{`  
  `    "order_id": 125,`  
  `    "customer_id": 1,`  
  `    "grand_total": 6250,`  
  `    "currency": "TRY"`  
  `  ``}`  
  `}`  
  Consumer’lar idempotent olmalıdır. Aynı event birden fazla kez
  geldiğinde işlem tekrarlanmamalıdır.  
  Başarısız mesajlar belirli sayıda tekrar denendikten sonra Dead Letter
  Queue’ya taşınmalıdır.  
    
  **Kullanılması beklenen patternler**  
  Strategy Pattern  
  Ödeme yöntemleri ortak bir sözleşme üzerinden çalışmalıdır:  
  `PaymentStrategy`  
  ` `├──` CreditCardPaymentStrategy`  
  ` `├──` BankTransferPaymentStrategy`  
  ` `└──` CashOnDeliveryPaymentStrategy`  
  Örnek metotlar:  
  `pay(paymentRequest)`  
  `refund(payment)`  
  `supports(paymentMethod)`  
  Kargo ücretinin hesaplanması için de ayrı stratejiler
  kullanılabilir:  
  `ShippingStrategy`  
  ` `├──` MockShippingStrategy`  
  ` `├──` ArasShippingStrategy`  
  ` `└──` Yurti``ç``iShippingStrategy`  
  **Factory Pattern**  
  İstek içerisinde gelen ödeme yöntemine göre uygun Strategy nesnesini
  seçmelidir.  
  `PaymentStrategyFactory.create(paymentMethod)`  
  Factory içerisinde uzun bir `if/else` veya `switch` zinciri yerine
  framework’ün dependency injection özelliklerinden yararlanılması
  tercih edilir.  
  **Repository Pattern**  
  Veri tabanı erişimi servis katmanından ayrılmalıdır.  
  Örnekler:  
  `OrderRepository`  
  `ProductRepository`  
  `PaymentRepository`  
  `OutboxEventRepository`  
  **Service Layer**  
  İş kuralları controller içerisinde bulunmamalıdır.  
  Örnekler:  
  `CreateOrderService`  
  `CancelOrderService`  
  `PaymentService`  
  `StockReservationService`  
  `ShipmentService`  
  **State Pattern veya kontrollü durum geçişleri**  
  Sipariş durumu rastgele değiştirilememelidir.  
  Örnek geçişler:  
  `pending -> processing`  
  `processing -> confirmed`  
  `processing -> failed`  
  `confirmed -> cancelled`  
  cancelled durumundaki bir sipariş tekrar `confirmed`
  yapılamamalıdır.  
  State Pattern kullanılması bonus olarak değerlendirilebilir. Daha
  basit bir state transition servisi de kabul edilebilir.  
  **Outbox Pattern**  
  Sipariş transaction’ı tamamlanmadan event yayınlanmamalıdır. Sipariş
  kaydı ve event kaydı aynı veri tabanı transaction’ında
  oluşturulmalıdır.  
  **Dependency Injection**  
  Controller, servis veya consumer sınıfları bağımlılıklarını doğrudan
  oluşturmamalıdır. Framework’ün dependency injection mekanizması
  kullanılmalıdır.  
    
  **Redis kullanım alanları**

<!-- -->

- Ürün listeleme cache’i

- Sipariş idempotency anahtarları

- Stok güncelleme kilidi

- Rate limiting

- Kısa süreli ödeme durumu

- Cache invalidation  
  Örnek anahtarlar:  
  `product:10`  
  `products:list:``{``filter_hash``}`  
  `idempotency:order:``{``key``}`  
  `lock:product-stock:``{``product_id``}`  
  `rate-limit:customer:``{``customer_id``}`  
  Redis kalıcı verinin ana kaynağı olmamalıdır.  
    
  **Hata formatı**  
  Tüm endpointler ortak hata formatı dönmelidir:  
  `{`  
  `  "error": ``{`  
  `    "code": "INSUFFICIENT_STOCK",`  
  `    "message": "Talep edilen ``ü``r``ü``n i``ç``in yeterli stok bulunmuyor.",`  
  `    "details": ``{`  
  `      "product_id": 10,`  
  `      "requested": 5,`  
  `      "available": 2`  
  `    ``}``,`  
  `    "correlation_id": "12c26a80-a8eb-49db-821d-c10e27a68f28"`  
  `  ``}`  
  `}`  
  Beklenen HTTP durum kodları:

<!-- -->

- `200`: Başarılı

- `201`: Kayıt oluşturuldu

- `400`: Hatalı istek

- `404`: Kayıt bulunamadı

- `409`: Stok veya state çakışması

- `422`: Validation hatası

- `429`: Rate limit

- `500`: Beklenmeyen hata  
    
  **Teknik beklentiler**

<!-- -->

- Proje tek komutla Docker üzerinde ayağa kalkmalıdır.

- Migration ve seed dosyaları bulunmalıdır.

- En az birkaç örnek müşteri, kategori ve ürün eklenmelidir.

- Controller sınıfları ince tutulmalıdır.

- İş kuralları service/use-case katmanında bulunmalıdır.

- Request ve response modelleri entity modellerinden ayrılmalıdır.

- Global exception handler kullanılmalıdır.

- Loglarda correlation ID bulunmalıdır.

- Birim ve entegrasyon testleri yazılmalıdır.

- API dokümantasyonu Swagger/OpenAPI ile sunulmalıdır.

- Şifre ve bağlantı bilgileri kaynak koda yazılmamalıdır.

- RabbitMQ consumer’ları retry ve Dead Letter Queue desteklemelidir.  
  **Minimum test senaryoları**

1.  Başarılı sipariş oluşturma

2.  Yetersiz stokla sipariş oluşturma

3.  Aynı idempotency anahtarıyla iki istek gönderme

4.  Ödeme stratejisinin doğru seçilmesi

5.  Başarısız ödeme sonrasında sipariş durumunun güncellenmesi

6.  İzin verilmeyen sipariş durum geçişi

7.  Aynı RabbitMQ event’inin iki kez tüketilmesi

8.  Polymorphic dosya ilişkisinin ürün ve sipariş için çalışması

9.  Outbox kaydı oluşmadan event yayınlanmaması

10. Transaction başarısız olduğunda siparişin yarım kaydedilmemesi  
    **Bonus görevler**

- Coupon/discount modülü geliştirilmesi

- İndirimlerin ürün veya kategoriye polymorphic olarak bağlanması

- Optimistic locking uygulanması

- OpenTelemetry veya benzeri tracing eklenmesi

- Prometheus metriklerinin sunulması

- Saga yaklaşımıyla başarısız ödeme sonrası stok rezervasyonunun geri
  alınması

- API authentication ve role-based authorization eklenmesi


## Swagger / OpenAPI

OpenAPI, endpointlerin parametrelerini, istek/yanıt modellerini ve HTTP işlemlerini
tanımlayan standarttır. Swagger UI bu dokümanı tarayıcıda gösterir ve API'ye
istek göndermeni sağlar. Bu projede `springdoc-openapi-starter-webmvc-ui:3.1.1`
controller ve DTO'lardan dokümanı otomatik üretir (Spring Boot 4 için springdoc 3.x).

Uygulama çalışırken:

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

Örneğin Swagger UI'da `GET /api/v1/products` işlemini aç, **Try it out** seç,
parametreleri gir ve **Execute** ile isteği gönder. Yanıt kodunu ve JSON gövdesini
aynı ekranda görebilirsin. POST/PUT/PATCH/DELETE işlemleri gerçek verileri değiştirir.

Kurulumun temel adımı `pom.xml` dosyasına springdoc bağımlılığını eklemektir.
`OpenApiConfig` başlık, sürüm ve açıklamayı belirler; `springdoc.paths-to-match`
dokümana `/api/v1/**` endpointlerini dahil eder. Temel doküman için controller'lara
ek annotation gerekmez. Daha ayrıntılı açıklamalar için `@Tag` (gruplama),
`@Operation` (işlem açıklaması), `@ApiResponse` (yanıt kodu/modeli) ve
`@Schema` (alan açıklaması/örneği) kullanılabilir. Otomatik dokümanı gerçek API
davranışıyla karşılaştırmak gerekir; özellikle özel hata kodları ek açıklama isteyebilir.

Resmi kurulum: https://springdoc.org/getting-started.html

## Yerel environment yapılandırması

### Geliştirme için seed data

`dev` profili açıldığında Liquibase 8 kategori, 24 müşteri, 48 ürün ve 30 müşteri
adresi ekler (`seed/realistic-seed.sql`). Gerçekçi ürün adları, farklı fiyatlar,
aktif/pasif kayıtlar ve stoksuz ürünler bulunur. Müşteriler ve adresler kurgusaldır;
e-postalar `example.com` kullanır. Fiyatlar güncel piyasa fiyatı iddiası taşımaz.
Sipariş, ödeme ve kargo verileri henüz eklenmez.

Environment değişkenlerini aşağıdaki gibi yükledikten sonra yerel Java uygulaması:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Tamamen Docker üzerinde çalıştırmak için:

```bash
SPRING_PROFILES_ACTIVE=dev docker compose up -d --build
```

IDE'de run configuration'ın active profiles alanına `dev` yazabilirsin.
Normal profil seed kayıt yüklemez. Liquibase seed değişikliğini bir kez uygular;
yeniden başlatmalarda kayıtlar çoğalmaz. Mevcut eşleşen e-posta, slug ve SKU
kayıtları değiştirilmez; ilişkiler sabit ID yerine bu alanlardan çözülür.
Profil kapatıldığında daha önce eklenen veriler silinmez. Gerçek veritabanında
`dev` profilini açma. Örnek API: `/api/v1/products?search=laptop&limit=3`.

Seed için Liquibase zorunlu değildir. Az sayıda manuel kayıt için REST API,
toplu aktarım için CSV + Liquibase `loadData`, service kurallarını çalıştırmak için
`@Profile("dev")` ile sınırlandırılmış `ApplicationRunner`, binlerce kayıt için
ayrı bir veri üretim/aktarım aracı kullanılabilir. Bu projede sürümlü, bir kez
uygulanan ve mevcut kayıtları koruyan bir başlangıç verisi için Liquibase seçildi.

Uygulama ve Liquibase Maven eklentisi bağlantı bilgilerini environment
değişkenlerinden alır. Compose da aynı MySQL/RabbitMQ kullanıcı ve şifre
değişkenlerini kullanır. Gerçek şifreler repoya eklenmez.

1. `cp .env.example .env` komutuyla yerel dosyanı oluştur.
2. `.env` içindeki üç boş şifreyi doldur. Dosya Git tarafından ignore edilir.
   Boşluk veya özel karakter içeren değerleri tek tırnak içine al.
3. Altyapıyı `docker compose up -d mysql redis rabbitmq` ile başlat. Compose `.env` dosyasını otomatik okur.
4. Java uygulamasını terminalden çalıştırmak için değişkenleri yükle:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

Spring Boot ve Maven `.env` dosyasını kendiliğinden okumaz. IDE'den çalıştırırken
aynı değişkenleri Java run configuration'ın environment alanına gir veya IDE'nin
desteklediği env-file ayarını kullan. Liquibase Maven komutlarını da değişkenleri
yüklediğin terminalden çalıştır.

Örnek dosyadaki host/port değerleri Java'nın bilgisayarında, MySQL/Redis/RabbitMQ'nun
Docker'da çalıştığı düzen içindir. MySQL portunu/veritabanı adını değiştirirsen
`ECOMMERCE_DB_URL` değerini de eşleştir. Eksik zorunlu ayarlar için kaynak kodda
varsayılan bağlantı/şifre bulunmaz.

Mevcut Docker volume'larında kullanıcı ve şifreler zaten oluşturulmuş olabilir.
`.env` dosyasına mevcut geçerli bilgileri gir; environment değerini değiştirmek
mevcut veritabanı kullanıcısının şifresini değiştirmez. Veri volume'larını silme.

Testler `application-test.properties` üzerinden H2 ve yalnızca teste özel
ayarlarla çalışır; `.env` gerektirmez:

```bash
./mvnw clean test
```
