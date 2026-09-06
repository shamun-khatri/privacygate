import os
import subprocess
from PIL import Image, ImageDraw, ImageFont

def generate_fixtures():
    os.makedirs("fixtures", exist_ok=True)

    # 1. Clean image: gradient / graphic without PII
    clean_img = Image.new("RGB", (1080, 1080), color=(30, 45, 60))
    draw_clean = ImageDraw.Draw(clean_img)
    draw_clean.rectangle([(100, 100), (980, 980)], fill=(45, 65, 85), outline=(100, 150, 200), width=4)
    draw_clean.text((200, 500), "Peaceful Mountain Trail", fill=(220, 240, 255))
    clean_path = os.path.join("fixtures", "clean_sample.jpg")
    clean_img.save(clean_path, quality=95)
    print(f"Created {clean_path}")

    # 2. Sensitive document: Invoice with phone number and email
    doc_img = Image.new("RGB", (1080, 1440), color=(255, 255, 255))
    draw_doc = ImageDraw.Draw(doc_img)
    draw_doc.rectangle([(40, 40), (1040, 1400)], outline=(30, 30, 30), width=3)
    draw_doc.rectangle([(40, 40), (1040, 160)], fill=(30, 40, 60))
    draw_doc.text((80, 80), "CONFIDENTIAL PAYMENT ADVICE", fill=(255, 255, 255))

    lines = [
        ("Vendor:", "TechConsult Private Ltd"),
        ("Invoice No:", "INV-2026-8941"),
        ("Date:", "05-Sep-2026"),
        ("Billing Support:", "billing-team@example.org"),
        ("Primary Mobile:", "+91 98765 43210"),
        ("Direct Line:", "+91 91234 56789"),
        ("Customer Account:", "user.finance@example.com"),
        ("Total Amount:", "INR 84,500.00"),
        ("Status:", "PENDING AUTHORIZATION"),
    ]

    y = 220
    for label, val in lines:
        draw_doc.text((80, y), label, fill=(100, 100, 100))
        draw_doc.text((380, y), val, fill=(20, 20, 20))
        draw_doc.line([(80, y + 45), (1000, y + 45)], fill=(230, 230, 230), width=1)
        y += 70

    draw_doc.rectangle([(80, y + 50), (1000, y + 160)], fill=(245, 245, 245), outline=(200, 200, 200))
    draw_doc.text((100, y + 80), "Please review carefully before distributing.", fill=(120, 50, 50))

    sens_path = os.path.join("fixtures", "sensitive_invoice.jpg")
    doc_img.save(sens_path, quality=95)
    print(f"Created {sens_path}")

if __name__ == "__main__":
    generate_fixtures()
