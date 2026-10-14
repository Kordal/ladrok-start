# Compose for TV, not Leanback

LadrokSetup and LadrokUpdater use Leanback, but the Launcher is written in Kotlin with Jetpack Compose for TV. Leanback's browse screen is the stock Android TV look (rows, headers, side panel), and removing that chrome takes more code than drawing a small grid. Compose gives full control over a minimal layout and handles D-pad focus itself.
