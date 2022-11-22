import Ionicons from '@expo/vector-icons/Ionicons';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { AnalyticsProvider, createClient, useAnalytics } from '@segment/analytics-react-native';
import * as Application from 'expo-application';
import { DeviceType, getDeviceTypeAsync } from "expo-device";
import * as ScreenOrientation from 'expo-screen-orientation';
import * as React from 'react';
import { useEffect, useState } from 'react';
import { Dimensions, Image, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import GeckoView from 'react-native-geckoview';
import { FlatGrid } from 'react-native-super-grid';
import * as Sentry from 'sentry-expo';
import { StatusBar } from 'expo-status-bar';

Sentry.init({
  dsn: 'https://2fe04faadb7e4798abdaa0ea0239c509@o54991.ingest.sentry.io/6247111',
  enableInExpoDevelopment: true,
  debug: true, // If `true`, Sentry will try to print out useful debugging information if something goes wrong with sending the event. Set it to `false` in production
});

// Segment.initialize({ androidWriteKey: 'FWluWjrs3bGCNU2p746VSRk16WxTu3Cf' })
const segmentClient = createClient({
  writeKey: 'FWluWjrs3bGCNU2p746VSRk16WxTu3Cf', 
  trackAppLifecycleEvents: true,
  flushInterval: 5,
});

function FeaturedScreen({navigation}) {
  const [items, setItems] = React.useState([
    { name: 'Netflix', code: '#1abc9c', uri: 'https://www.netflix.com/', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/Netflix-cover.png' },
    { name: 'Paramount+', code: '#2ecc71', uri: 'https://dartdis.co/paramount-plus', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/ParamountPlus-cover.png' },
    { name: 'discovery+', code: '#3498db', uri: 'https://dartdis.co/discovery', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/DiscoveryPlus-cover.png' },
    { name: 'Disney+', code: '#9b59b6', uri: 'https://www.disneyplus.com/', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/DisneyPlus-cover.png' },
    { name: 'Hulu', code: '#34495e', uri: 'https://www.hulu.com/', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/Hulu-cover.png' },
    { name: 'Audible', code: '#16a085', uri: 'https://www.audible.com/', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/Audible-cover.png' },
    { name: 'Calm', code: '#27ae60', uri: 'https://www.calm.com/', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/Calm-cover.png' },
    { name: 'MasterClass', code: '#2980b9', uri: 'https://www.masterclass.com/', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/MasterClass-cover.png' },
    { name: 'Twitch', code: '#8e44ad', uri: 'https://www.twitch.tv/', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/Twitch-cover.png' },
    { name: 'Pluto TV', code: '#2c3e50', uri: 'https://pluto.tv', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/PlutoTV-cover.png' },
    { name: 'Tubi', code: '#f1c40f', uri: 'https://tubitv.com', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/Tubi-cover.png' },
    { name: 'Peacock', code: '#e67e22', uri: 'https://www.peacocktv.com/', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/Peacock-cover.png' },
    { name: 'HBO MAX', code: '#e74c3c', uri: 'https://www.hbomax.com/', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/HBO-MAX-cover.png' },
    { name: 'NPR', code: '#ecf0f1', uri: 'https://www.npr.org/', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/NPR-cover.png' },
    { name: 'Blinkist', code: '#95a5a6', uri: 'https://dartdis.co/blinkist', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/Blinkist-cover.png' },
    { name: 'TikTok', code: '#f39c12', uri: 'https://www.tiktok.com/', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/TikTok-cover.png' },
    { name: 'Dark Sky', code: '#f39c12', uri: 'https://darksky.net/', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/Dark-Sky-cover.png' },
    { name: 'Apple TV+', code: '#f39c12', uri: 'https://tv.apple.com/', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/Apple-TV-cover.png' },
    { name: 'Prime Video', code: '#f39c12', uri: 'https://www.amazon.com/Amazon-Video/b?ie=UTF8&node=2858778011', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/Prime-Video-cover.png' },
    { name: 'Hot Ones', code: '#f39c12', uri: 'https://firstwefeast.com/tag/hot-ones', thumbnail: 'https://dart-discover.s3.amazonaws.com/thumbnails/Hot-Ones-cover.png' },
  ]);

  const window = Dimensions.get('window')
  
  async function getOrientation() {
    const orientation = await ScreenOrientation.getOrientationAsync()
    console.log('window:', window, 'orientation:', orientation)
    console.log('itemDimension:', (window.width - 50) / 4)
  }
  getOrientation()

  async function getDeviceType() {
    const deviceTypeMap = {
      [DeviceType.UNKNOWN]: "unknown",
      [DeviceType.PHONE]: "phone",
      [DeviceType.TABLET]: "tablet",
      [DeviceType.DESKTOP]: "desktop",
      [DeviceType.TV]: "tv",
    };
    useEffect(() => {
      getDeviceTypeAsync().then((deviceType) => {
        console.log('deviceType:', deviceTypeMap[deviceType]);
      });
    }, []);
    return null
  }
  getDeviceType()
  
  return (
    <View style={ styles.container}>
      <FlatGrid
        itemDimension={(window.width - 50) / 4 }
        // itemDimension={200}
        data={items}
        style={styles.gridView}
        // staticDimension={300}
        // fixed
        spacing={5}
        renderItem={({ item }) => (
          <TouchableOpacity onPress={() => navigation.navigate('Theater', {
            uri: item.uri, name: item.name}
          )}>
            <Image style={[styles.itemContainer,{resizeMode: 'cover'}]} source={{uri: item.thumbnail}}/>
            {/* <View style={[styles.itemContainer, { backgroundColor: 'green', borderColor: item.code, borderWidth: 4 }]}> */}
              {/* <Text style={styles.itemName}>{item.name}</Text>   */}
              {/* <Text style={styles.itemCode}>{item.code}</Text> */}
            {/* </View> */}
          </TouchableOpacity>
        )}
      />
    </View>
  );
}

function TheaterScreen({ route, navigation }) {
  const { name, uri } = route.params;
  const { track } = useAnalytics();
  track('Pressed', {name} );

  return (
    <View style={{ flex: 1}}>
      {/* <Text>{name} | {uri}</Text> */}
      {/* <Button title="Go back" onPress={() => navigation.goBack()} /> */}
      <GeckoView style={{flex: 1}} source={{uri}} />
    </View>
  );
}

function WatchScreen() {
  return (
    <View style={{ flex: 1 }}>
      <GeckoView style={{flex: 1}} source={{uri: 'https://youtube.com'}} />
    </View>
  );
}

function ListenScreen() {
  return (
    <View style={{ flex: 1 }}>
      <GeckoView style={{flex: 1}} source={{uri: 'https://tidal.com/'}} />
    </View>
  );
}

function SettingsScreen() {
  return (
    <View style={styles.container}>
      <Image
        style={styles.dartLogo}
        source={require('./assets/DART.png')}
      />
      <Text style={styles.appVersion}>{Application.applicationName}</Text>
      <Text style={styles.appVersion}>{Application.nativeApplicationVersion} ({Application.nativeBuildVersion})</Text>
    </View>
  );
}

const Tab = createBottomTabNavigator();
const FeaturedStack = createNativeStackNavigator();

function FeaturedStackScreen() {
  return (
    <FeaturedStack.Navigator
      screenOptions={({ route }) => ({
        headerStyle: {
          backgroundColor: '#242526',
        },
        headerTintColor: '#bdc3c7',
        headerTitleAlign: 'center',
        headerTitleStyle: {
          fontWeight: 'normal',
          color: '#bdc3c7',
        }})}
      >
      <FeaturedStack.Screen name="Channels" component={FeaturedScreen} />
      <FeaturedStack.Screen name="Theater" component={TheaterScreen}  options={({ route }) => ({ title: route.params.name })}/>
    </FeaturedStack.Navigator>
  );
}

const getActiveRouteName = state => {
  if (!state || typeof state.index !== 'number') {
    return 'Unknown';
  }

  const route = state.routes[state.index];

  if (route.state) {
    return getActiveRouteName(route.state);
  }

  return route.name;
};

export default function App() {
  const [routeName, setRouteName] = useState('Unknown');
  
  // const [device, setDevice] = useState(undefined)
  // //Use `useEffect` to prevent recurring checking
  // useEffect(() => {
  //   getDeviceType().then((a) => {
  //     //Update (rerender) the component with the device info
  //     setDevice(a)
  //   })
  // }, [])

  return (
    <AnalyticsProvider client={segmentClient}>
      <NavigationContainer
       onStateChange={(state) => {
        const newRouteName = getActiveRouteName(state);
     
        if (routeName !== newRouteName) {
          segmentClient.screen(newRouteName);
          setRouteName(newRouteName);
        }
      }}
      >
        <Tab.Navigator
          screenOptions={({ route }) => ({
            tabBarIcon: ({ focused, color, size }) => {
              let iconName;

              if (route.name === 'Featured') {
                iconName = focused ? 'ios-apps' : 'ios-apps-outline';
              } else if (route.name === 'Watch' || route.name === 'YouTube') {
                iconName = focused ? 'ios-tv' : 'ios-tv-outline';
              } else if (route.name === 'Listen' || route.name === 'TIDAL') {
                iconName = focused ? 'ios-musical-notes' : 'ios-musical-notes-outline';
              } else if (route.name === 'Settings') {
                iconName = focused ? 'ios-settings-sharp' : 'ios-settings-outline';
              }

              return <Ionicons name={iconName} size={size} color={color} />;
            },
            tabBarActiveTintColor: '#9b59b6',
            tabBarInactiveTintColor: '#bdc3c7',
            tabBarStyle: { backgroundColor: '#242526', borderTopColor: 'black'},
            tabBarHideOnKeyboard: true,
            headerShown: false,
            headerStyle: {
              backgroundColor: '#242526',
            },
            // headerTintColor: '#bdc3c7',
            headerTitleStyle: {
              fontWeight: 'normal',
              color: '#bdc3c7'
            },
          })}
        >
          <Tab.Screen name="Featured" component={FeaturedStackScreen} />
          <Tab.Screen name="YouTube" component={WatchScreen} />
          <Tab.Screen name="TIDAL" component={ListenScreen} />
          <Tab.Screen name="Settings" component={SettingsScreen} />
        </Tab.Navigator>
      </NavigationContainer>
      {/* <StatusBar hidden={true} /> */}
    </AnalyticsProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#18191a',
    alignItems: 'center',
    justifyContent: 'center',
  },
  gridView: {
    marginTop: 0,
    flex: 1,
  },
  itemContainer: {
    // justifyContent: 'flex-end',
    borderRadius: 5,
    padding: 10,
    height: 160,
  },
  itemName: {
    fontSize: 16,
    color: '#f7f7ff',
    fontWeight: '600',
  },
  // itemCode: {
  //   fontWeight: '600',
  //   fontSize: 12,
  //   color: '#fff',
  // },
  dartLogo: {
    height: 160,
    width: 160,
    resizeMode: 'contain',
    marginBottom: 5,
    marginBottom: 50
  },
  appVersion: {
    fontWeight: 'normal',
    fontSize: 16,
    color: '#fff'
  }
})