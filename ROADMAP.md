# Mini E-Ticaret API Roadmap

Bu plan `README.md` içindeki iş akışını temel alır. Geliştirme sırası önce temel
CRUD işlemlerini tamamlamak, ardından listeleme iyileştirmeleri ve ortak
altyapıyı kurmak, en son sipariş yaşam döngüsüne geçmek şeklindedir. Amaç her
tabloya otomatik CRUD açmak değil; yönetim verilerini CRUD, sipariş yaşam
döngüsünü ise iş kurallarına sahip use-case endpointleri üzerinden yönetmektir.

## CRUD kapsamı

| Kaynak                   |    Create | Read/List |    Update |  Delete | Yaklaşım                                                                                                    |
| ------------------------ | --------: | --------: | --------: | ------: | ----------------------------------------------------------------------------------------------------------- |
| `customers`              |      Evet |      Evet |      Evet | Koşullu | Fiziksel silme yerine `PASSIVE` durumu tercih edilmeli. Siparişi olan müşteri silinmemeli.                  |
| `categories`             |      Evet |      Evet |      Evet | Koşullu | Ürünü olan kategori silinmemeli; pasifleştirme tercih edilmeli.                                             |
| `products`               |      Evet |      Evet |      Evet | Koşullu | Fiziksel silme yerine `PASSIVE`; stok değişimi ayrı operasyon olmalı. Listeleme filtreli ve sayfalı olmalı. |
| müşteri `addresses`      |      Evet |      Evet |      Evet |    Evet | Sadece `addressable_type=CUSTOMER` için bağımsız CRUD.                                                      |
| sipariş `addresses`      | Siparişle | Siparişle |     Hayır |   Hayır | Sipariş anındaki snapshot; sipariş use-case'i oluşturur.                                                    |
| `attachments`            |    Upload |      Evet |     Hayır |    Evet | Metadata CRUD yerine dosya yükleme/indirme/silme operasyonları. Bağlanan kaydın varlığı doğrulanmalı.       |
| `orders`                 |  Use-case |      Evet |     Hayır |   Hayır | `create`, `detail`, `status`, `cancel`; genel update/delete olmamalı.                                       |
| `order_items`            | Siparişle | Siparişle |     Hayır |   Hayır | Siparişin immutable fiyat/ürün snapshot'ı. Ayrı controller gereksiz.                                        |
| `payments`               |  Use-case |      Evet |     Hayır |   Hayır | `start` ve `refund`; durumları consumer/strateji yönetir.                                                   |
| `shipments`              |  Otomatik |      Evet | Operasyon |   Hayır | Ödeme sonrası oluşturulur; `ship`/`deliver` gibi kontrollü geçişler kullanılabilir.                         |
| `order_status_histories` |  Otomatik |      Evet |     Hayır |   Hayır | Audit kaydı; yalnızca durum geçiş servisi yazar.                                                            |
| `outbox_events`          |  Otomatik |    Dahili |     Hayır |   Hayır | Public API açılmaz; worker ve operasyonel gözlem için kullanılır.                                           |

Tüm public endpointler `/api/v1` prefix'i kullanmalıdır.

## Faz 0 — CRUD için minimum temel

- [x] Tek migration kaynağı olarak Liquibase kullan.
- [x] Entity ve migration uyumunu `ddl-auto=validate` ile doğrula.
- [x] CRUD endpointlerinde request/response DTO'ları kullan.
- [x] DTO alanlarında temel Jakarta Validation kurallarını tanımla.
- [x] Service testleri için Mockito, repository testleri için H2 test altyapısını
      hazırla.
- [x] Uygulamanın MySQL ile açıldığını ve Liquibase migration'ının çalıştığını
      doğrula.

Çıkış kriteri: CRUD geliştirmeye başlanabilmesi için veritabanı şeması hazırdır,
uygulama açılır ve testler harici servislere ihtiyaç duymadan çalışır.

## Faz 1 — Temel yönetim CRUD'ları

Bu fazda sayfalama, cache, mesaj kuyruğu ve gelişmiş eşzamanlılık konuları yoktur.
Öncelik controller-service-repository akışını ve temel iş kurallarını
tamamlamaktır.

### Categories

- [x] Listele, detay, oluştur, güncelle, sil servis/controller iskeleti
- [x] Endpointleri `/api/v1/categories` altında standardize et.
- [x] Silmeden önce kategoriye bağlı ürün kontrolü ekle.
- [x] Service testlerini ekle.
- [x] Controller ve repository testlerini ekle.

### Customers

- [x] `POST /api/v1/customers`
- [x] `GET /api/v1/customers` temel listeleme
- [x] `GET /api/v1/customers/{id}`
- [x] `PUT /api/v1/customers/{id}`
- [x] `PATCH /api/v1/customers/{id}/status`
- [x] E-posta unique kontrolü ve normalize işlemi
- [x] Service testlerini ekle.
- [x] Controller ve repository testlerini ekle.

### Products

- [x] `POST /api/v1/products`
- [x] `GET /api/v1/products` temel listeleme
- [x] `GET /api/v1/products/{id}`
- [x] `PUT /api/v1/products/{id}`
- [x] `PATCH /api/v1/products/{id}/status`
- [x] `PATCH /api/v1/products/{id}/stock` temel stok güncellemesi
- [x] SKU unique, fiyat ve stok negatif olamaz kontrolleri
- [x] Service testlerini ekle.
- [x] Controller ve repository testlerini ekle.

### Customer addresses

- [x] `POST /api/v1/customers/{customerId}/addresses`
- [x] `GET /api/v1/customers/{customerId}/addresses`
- [x] `GET /api/v1/customers/{customerId}/addresses/{id}`
- [x] `PUT /api/v1/customers/{customerId}/addresses/{id}`
- [x] `DELETE /api/v1/customers/{customerId}/addresses/{id}`
- [x] Adresin gerçekten ilgili müşteriye ait olduğunu her işlemde doğrula.
- [x] Address request/response DTO'larını oluştur.
- [x] Address repository, service ve controller testlerini ekle.

Çıkış kriteri: Category, Customer, Product ve Customer Address CRUD'ları DTO
kullanır, entity'leri doğrudan dışarı vermez ve repository/service/controller
testleri geçer.

## Faz 2 — README API sözleşmesi, listeleme ve çalıştırma altyapısı

Önce mevcut CRUD sözleşmesini README ile eşitle, ardından ürün listeleme ve
çalıştırma altyapısını tamamla. Customer sayfalaması ek kapsamdır; README'deki
ürün listeleme gereksinimlerinden sonra ele alınmalıdır.

- [x] JSON request/response alanlarını `snake_case` olarak standardize et.
- [x] JSON enum değerlerini küçük harf olarak sun ve kabul et.
- [x] Correlation ID'yi request boyunca MDC, response header ve hata gövdesinde taşı.
- [x] API sözleşmesi için controller testlerini güncelle; hata formatını test et.
- [x] Uygulama için Dockerfile ve Compose servisi ekle; tüm sistemi tek komutla başlatmayı doğrula.
- [x] Şifreleri ve bağlantı bilgilerini kaynak koddan çıkar; environment değişkenlerini kullan.
      Uygulama, Compose ve Liquibase Maven ayarları taşındı; `.env.example` ve çalıştırma yönergeleri eklendi.
      109 test ve örnek değişkenlerle Compose yapılandırma kontrolü geçti.

- [x] Customer listesine `page`, `limit` ve toplam kayıt/sayfa bilgisi ekle.
- [x] Customer sayfalaması için repository/service/controller testlerini ekle.
- [x] Customer listesine `status` ve ad/e-posta için `search` desteği ekle.
- [x] Product listesine `page`, `limit` ve toplam kayıt/sayfa bilgisi ekle.
- [x] Product sayfalaması için repository/service/controller testlerini ekle.
- [x] Product listesine `category_id` filtresi ve repository/service/controller testlerini ekle.
- [x] Product listesine `status` filtresini ekle; kategoriyle birlikte çalışmasını ve sayfalı sonuçları test et.
- [x] Product adı/SKU için `search` desteği ve filtrelerle birlikte sayfalama testlerini ekle.
- [x] Product listesine `sort` desteği ekle (price/name/created_at, asc/desc, ID ile eşitlik çözümü).
- [x] Customer status/search filtreleri için repository/service/controller testleri ekle.
      Product listeleme testleri tamamlandı.
- [x] Ortak `error: {code, message, details, correlation_id}` modeli ve `@RestControllerAdvice` ekle.
- [x] Bozuk JSON/tip hatası için `400`, bulunamayan kayıt için `404`, çakışma için
      `409`, DTO validation hatası için `422` döndür.
- [ ] Stok güncellemesini optimistic locking ile yarış durumuna karşı güvenceye
      al. **Ertelendi:** Kullanıcının isteğiyle daha sonra ele alınacak; henüz uygulanmadı.
- [x] Seed olarak birkaç müşteri, kategori ve ürün ekle.
- [x] Swagger/OpenAPI ekle.

Çıkış kriteri: Liste endpointleri sayfalı ve filtrelidir; bütün CRUD hataları
standart cevap döner ve stok güncellemesi eşzamanlı isteklere karşı güvenlidir.

## Faz 3 — Sipariş çekirdeği

- [x] `POST /api/v1/orders` request/response DTO'larını oluştur.
- [ ] README akışına uygun Redis `Idempotency-Key` kontrolü ekle; aynı anahtara
      önceki cevabı döndür ve eşzamanlı tekrar istekleri test et.
- [x] Müşteri, ürün, aktiflik ve stok doğrulamalarını yap.
- [x] Stok rezervasyonunu transaction ve atomik stok güncellemesi veya Redis
      distributed lock ile güvenceye al; optimistic locking ek koruma olabilir.
- [x] Sipariş, kalem snapshot'ları, sipariş adresi ve `order.created` outbox
      kaydını tek transaction içinde oluştur.
- [x] `GET /api/v1/orders/{id}` ve `/status` endpointlerini ekle.
- [x] Kontrollü durum geçiş servisini ve durum geçmişini ekle.
- [x] `POST /api/v1/orders/{id}/cancel` ve stok telafisini ekle.

Çıkış kriteri: Tekrarlanan idempotency anahtarı yeni sipariş üretmez; yetersiz
stok ve transaction rollback testleri geçer.

## Faz 4 — Redis, asenkron altyapı ve ödeme

- [x] Docker Compose'a Redis ve RabbitMQ servislerini ekle.
- [ ] Redis idempotency süresi, başarısız işlem ve tekrar istek davranışlarını doğrula.
- [ ] Product liste/detay cache ve mutation sonrası cache invalidation ekle.
- [ ] Outbox publisher'ı retry bilgisiyle birlikte geliştir.
- [ ] RabbitMQ exchange, queue, routing key, retry ve DLQ tanımlarını ekle.
- [x] Event envelope (`event_id`, `event_type`, `occurred_at`,
      `correlation_id`, `data`) standardını uygula.
- [ ] README'deki zorunlu eventleri üret ve tüket: `order.created`, `stock.reserved`,
      `stock.reservation_failed`, `payment.requested`, `payment.completed`,
      `payment.failed`, `order.confirmed`, `order.cancelled`, `shipment.created`,
      `notification.requested`. **Kısmen tamamlandı:** `order.created`,
      `payment.requested`, `payment.completed`, `payment.failed`,
      `order.confirmed` ve `order.cancelled` outbox kayıtları üretiliyor;
      publisher ve consumer akışı henüz yok.
- [ ] Consumer idempotency mekanizması ekle.
- [x] `PaymentStrategy`, kredi kartı/banka havalesi/kapıda ödeme
      implementasyonları ve DI tabanlı `PaymentStrategyFactory` ekle.
- [x] `POST /api/v1/orders/{id}/payments` endpointini ekle; mükerrer aktif
      ödemeyi ve eşzamanlı istekleri pessimistic lock ile engelle.
- [x] `POST /api/v1/payments/{id}/refund` endpointini idempotent olarak ekle.
- [x] Başarılı ödemede siparişi `CONFIRMED`, başarısız ödemede
      `FAILED` durumuna geçir; başarısızlıkta rezerve stoğu geri bırak.
- [x] Payment service, controller ve transaction rollback entegrasyon testlerini ekle.

Çıkış kriteri: Doğru ödeme stratejisi seçilir, aynı event iki kere yan etki
oluşturmaz ve başarısız mesaj DLQ'ya gider.

## Faz 5 — Kargo, dosya ve bildirim

- [ ] `ShippingStrategy` ve DI tabanlı factory ekle.
- [ ] Başarılı ödeme sonrası shipment oluştur.
- [ ] Kargo durumlarını kontrollü geçişlerle yönet.
- [ ] Multipart attachment upload, metadata, indirme ve silme operasyonlarını ekle.
- [ ] MIME type, boyut, izin verilen attachment/type eşleşmesi ve path güvenliği
      doğrulamalarını ekle.
- [ ] Bildirim consumer'ını idempotent şekilde simüle et.

## Faz 6 — Gözlemlenebilirlik, kalite ve teslim

- [x] HTTP correlation ID filter ve log formatını ekle.
- [ ] Correlation ID'yi Outbox/RabbitMQ eventleri ve consumer loglarına aktar.
- [x] MySQL, Redis ve RabbitMQ için Docker Compose health check'leri ekle.
- [ ] README'deki 10 minimum senaryoyu otomatik teste dönüştür.
- [ ] Testcontainers ile MySQL/Redis/RabbitMQ entegrasyon testleri ekle.
- [ ] OpenAPI örneklerini ve Postman/Bruno koleksiyonunu tamamla.
- [ ] Teslim öncesi kaynak kodda secret bulunmadığını doğrula (uygulama Faz 2).
- [ ] Rate limit ve operasyonel health/metrics kontrollerini ekle.
- [ ] Son aşamada tracing ve Saga bonuslarını değerlendir.

## Önerilen uygulama sırası

1. Category CRUD (tamamlandı)
2. Customer CRUD (temel sürüm tamamlandı)
3. Product CRUD (temel sürüm tamamlandı)
4. Customer Address CRUD (tamamlandı)
5. README API sözleşmesi ve ortak hata cevapları (mevcut CRUD'lar tamamlandı)
6. Product sayfalama/filtreleme/sıralama (tamamlandı); ardından Customer listeleme iyileştirmeleri
7. Seed, OpenAPI, environment yapılandırması ve uygulamanın Docker'a alınması
8. Order create/detail/cancel ve durum geçişleri (tamamlandı; Redis idempotency bekliyor)
9. Payment strategy/factory, ödeme başlatma ve refund (tamamlandı)
10. Redis idempotency, Outbox publisher ve RabbitMQ
11. Shipment stratejileri ve durum akışı
12. Attachments, bildirimler ve uçtan uca testler

## Şu an sıradaki iş

Sipariş çekirdeği ve senkron ödeme akışı tamamlandı. Ödeme yöntemi
request içindeki `PaymentMethod` ile seçiliyor; DI tabanlı factory uygun strategy'yi
çözümlüyor. Başarılı/başarısız ödeme, stok telafisi, sipariş durum
geçişleri, refund ve ilgili outbox kayıtları testlerle kapsanıyor.

Son doğrulama: `./mvnw test` — 164 test geçti.

Sıradaki öncelikler:

1. Sipariş oluşturmada Redis `Idempotency-Key` desteği ve eşzamanlı tekrar testleri
2. Outbox publisher ile RabbitMQ exchange/queue/routing key, retry ve DLQ altyapısı
3. Eksik eventlerin üretilmesi ve idempotent consumer mekanizması
4. Product cache/cache invalidation ve ertelenen optimistic locking çalışması
5. Shipment strategy ve başarılı ödeme sonrası kargo oluşturma
6. Attachment ve bildirim akışları

Product optimistic locking ve eşzamanlılık testleri daha sonraya ertelendi;
bu nedenle Faz 2'nin ilgili çıkış kriteri henüz tam karşılanmıyor. Faz 3'te
Redis idempotency, Faz 4'te ise mesaj yayınlama/tüketme altyapısı eksik.
