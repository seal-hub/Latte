
BASE_DIR="../Apps"
data = {'Geek': ("geek.apk", "com.contextlogic.geek"),
	'ToDoList': ('todolist.apk', 'com.splendapps.splendo'),
	'Budget': ('budgettracker.apk', 'com.colpit.diamondcoming.isavemoney'),
	'Calorie': ('caloriecounter.apk', 'com.fatsecret.android'),
	'School': ('schoolplanner.apk', 'daldev.android.gradehelper'),
	'Bill': ('billreminder.apk', 'com.aa3.easybillsreminder'),
	'Dictionary': ('dictionary.apk', 'com.dictionary'),
	'Fuelio': ('fuelio.apk', 'com.kajda.fuelio'),
	'Walmart': ('walmart.apk', 'com.walmart.android'),
	'Clock' : ('clock.apk', 'hdesign.theclock'),
	'Cook': ('cookpad.apk', 'com.mufumbo.android.recipe.search'),
	'Trip': ('tripit.apk', 'com.tripit'),
	'Soundcloud': ('soundcloud.apk', 'com.soundcloud.android'),
	'Viemo': ('vimeo.apk', 'com.vimeo.android.videoapp'),
	'Ziprecruiter': ('ziprecruiter.apk', 'com.ziprecruiter.android.release'),
	'Feedly': ('feedly.apk', 'com.devhd.feedly'),
	'Yelp': ('yelp.apk', 'com.yelp.android'),
	'Astro': ('astro.apk', 'com.metago.astro'),
	'Checkout': ('checkout.apk', 'com.c51'),
	'iPlayer': ('iPlayer.apk', 'com.bbc.globaliplayerradio.international'),
	}

import sys
cmd = sys.argv[1]
path = sys.argv[2]
key = path.split('/')[-1].split('_')[0]
result = data.get(key, ('WRONG','WRONG'))
if cmd == 'apk':
    print(BASE_DIR+'/'+result[0])
else:
    print(result[1])


