import os
from PIL import Image, ImageDraw

source_image_path = r"C:\Users\Pawan Kumar\.gemini\antigravity\brain\816f88ac-6aa7-464f-a110-b0a83f6434bd\premium_watch_icon_1771658973289.png"
res_dir = r"d:\My project\my app\NF watch\app\src\main\res"

sizes = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192
}

def create_round_crop(img):
    mask = Image.new("L", img.size, 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0) + img.size, fill=255)
    result = img.copy()
    result.putalpha(mask)
    return result

img = Image.open(source_image_path).convert("RGBA")

# Generate standard and round mipmaps
for density, size in sizes.items():
    mipmap_dir = os.path.join(res_dir, f"mipmap-{density}")
    os.makedirs(mipmap_dir, exist_ok=True)
    
    resized = img.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(os.path.join(mipmap_dir, "ic_launcher.png"))
    
    round_img = create_round_crop(resized)
    round_img.save(os.path.join(mipmap_dir, "ic_launcher_round.png"))

# Generate adaptive foreground
fg_dir = os.path.join(res_dir, "drawable")
os.makedirs(fg_dir, exist_ok=True)
fg_size = 432
fg_img = img.resize((fg_size, fg_size), Image.Resampling.LANCZOS)
fg_img.save(os.path.join(fg_dir, "ic_launcher_foreground.png"))

# Attempt to remove XML foreground if it exists
xml_path = os.path.join(fg_dir, "ic_launcher_foreground.xml")
if os.path.exists(xml_path):
    os.remove(xml_path)

print("Icons generated successfully!")
