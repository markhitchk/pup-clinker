import React from 'react';
import {GameHost} from '../../src/components/GameHost.jsx';
export function PuppyPlatform({children,name}){
 return <GameHost.div className="puppy-platform">
  <GameHost.div className="platform-paws" aria-hidden="true"><GameHost.span>🐾</GameHost.span><GameHost.span>🐾</GameHost.span><GameHost.span>🐾</GameHost.span><GameHost.span>🐾</GameHost.span></GameHost.div>
  <GameHost.div className="platform-orbit" aria-hidden="true"/>
  <GameHost.div className="platform-portrait">{children}</GameHost.div>
  <GameHost.div className="platform-name"><GameHost.h2 title={name}>{name}</GameHost.h2><GameHost.span className="current-puppy-badge">Current Puppy</GameHost.span></GameHost.div>
 </GameHost.div>;
}
