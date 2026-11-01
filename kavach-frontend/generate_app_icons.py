from PIL import Image, ImageDraw
import os

base_dir = os.path.abspath(os.path.dirname(__file__))
source_path = os.path.join(base_dir, "app", "src", "main", "res", "drawable", "kavach_logo.png")
playstore_dir = os.path.join(base_dir, "playstore_assets")
res_dir = os.path.join(base_dir, "app", "src", "main", "res")

if os.path.exists(source_path):
    img = Image.open(source_path).convert("RGBA")

    # 1. 512x512 Google Play Icon with comfortable padding
    # Solid dark background behind the circular emblem
    icon_512 = Image.new("RGBA", (512, 512), (7, 10, 15, 255))
    logo_512_size = 390
    logo_512_scaled = img.resize((logo_512_size, logo_512_size), Image.Resampling.LANCZOS)
    offset_512 = (512 - logo_512_size) // 2
    icon_512.paste(logo_512_scaled, (offset_512, offset_512), logo_512_scaled)
    icon_512.save(os.path.join(playstore_dir, "icon_512.png"), "PNG")
    print("Generated 512x512 Play Store icon with padding")

    # 2. 1024x500 Google Play Feature Graphic
    banner = Image.new("RGBA", (1024, 500), (7, 10, 15, 255))
    logo_size = 360
    logo_scaled = img.resize((logo_size, logo_size), Image.Resampling.LANCZOS)
    x_offset = (1024 - logo_size) // 2
    y_offset = (500 - logo_size) // 2
    banner.paste(logo_scaled, (x_offset, y_offset), logo_scaled)
    banner.convert("RGB").save(os.path.join(playstore_dir, "feature_graphic.png"), "PNG")
    print("Generated 1024x500 Feature Graphic")

    # 3. Android Mipmap and Drawable Icons
    sizes = {
        "mdpi": (48, "drawable-mdpi", "mipmap-mdpi"),
        "hdpi": (72, "drawable-hdpi", "mipmap-hdpi"),
        "xhdpi": (96, "drawable-xhdpi", "mipmap-xhdpi"),
        "xxhdpi": (144, "drawable-xxhdpi", "mipmap-xxhdpi"),
        "xxxhdpi": (192, "drawable-xxxhdpi", "mipmap-xxxhdpi"),
    }

    def make_round(im, size):
        mask = Image.new('L', (size, size), 0)
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, size - 1, size - 1), fill=255)
        output = Image.new('RGBA', (size, size), (7, 10, 15, 0))
        output.paste(im, (0, 0))
        output.putalpha(mask)
        return output

    for density, (size, draw_folder, mip_folder) in sizes.items():
        draw_path = os.path.join(res_dir, draw_folder)
        mip_path = os.path.join(res_dir, mip_folder)
        os.makedirs(draw_path, exist_ok=True)
        os.makedirs(mip_path, exist_ok=True)

        # 1. Full square icon with comfortable edge padding
        inner_size = int(size * 0.78)
        square_icon = Image.new("RGBA", (size, size), (7, 10, 15, 255))
        resized = img.resize((inner_size, inner_size), Image.Resampling.LANCZOS)
        offset = (size - inner_size) // 2
        square_icon.paste(resized, (offset, offset), resized)
        square_icon.save(os.path.join(mip_path, "ic_launcher.png"), "PNG")
        square_icon.save(os.path.join(draw_path, "ic_launcher.png"), "PNG")

        # 2. Round icon for circular launcher masks
        round_inner_size = int(size * 0.76)
        round_base = Image.new("RGBA", (size, size), (7, 10, 15, 255))
        round_resized = img.resize((round_inner_size, round_inner_size), Image.Resampling.LANCZOS)
        round_offset = (size - round_inner_size) // 2
        round_base.paste(round_resized, (round_offset, round_offset), round_resized)
        round_icon = make_round(round_base, size)
        round_icon.save(os.path.join(mip_path, "ic_launcher_round.png"), "PNG")
        round_icon.save(os.path.join(draw_path, "ic_launcher_round.png"), "PNG")

        # 3. Adaptive icon foreground (108dp canvas, emblem centered in 62dp safe zone)
        adaptive_canvas = int(size * 108 / 48)
        adaptive_icon_size = int(size * 62 / 48)
        canvas = Image.new("RGBA", (adaptive_canvas, adaptive_canvas), (0, 0, 0, 0))
        fg_scaled = img.resize((adaptive_icon_size, adaptive_icon_size), Image.Resampling.LANCZOS)
        fg_offset = (adaptive_canvas - adaptive_icon_size) // 2
        canvas.paste(fg_scaled, (fg_offset, fg_offset), fg_scaled)
        
        # Save foreground to both mipmap and drawable densities for maximum OS compatibility
        canvas.save(os.path.join(mip_path, "ic_launcher_foreground.png"), "PNG")
        canvas.save(os.path.join(draw_path, "ic_launcher_foreground.png"), "PNG")

        print(f"Generated {density}: {size}x{size} square & round, {adaptive_canvas}x{adaptive_canvas} foreground")

    print("All app icons regenerated across mipmap and drawable densities!")
else:
    print(f"Source image not found at {source_path}")
